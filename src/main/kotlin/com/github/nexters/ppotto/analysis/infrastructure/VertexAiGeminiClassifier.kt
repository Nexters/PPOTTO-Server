package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.AnalysisErrorCode
import com.github.nexters.ppotto.analysis.domain.GeminiClassifier
import com.github.nexters.ppotto.analysis.domain.PhotoRef
import com.github.nexters.ppotto.analysis.domain.RecapContent
import com.github.nexters.ppotto.analysis.domain.StickerRegenerationTarget
import com.github.nexters.ppotto.analysis.domain.ThemeClassification
import com.github.nexters.ppotto.analysis.domain.ThemeClassificationValidator
import com.github.nexters.ppotto.analysis.domain.ThemeComment
import com.github.nexters.ppotto.global.config.VertexAiProperties
import com.github.nexters.ppotto.global.error.BusinessException
import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.HttpOptions
import com.google.genai.types.HttpRetryOptions
import com.google.genai.types.Part
import com.google.genai.types.Schema
import com.google.genai.types.Type
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@Component
class VertexAiGeminiClassifier(
    private val genAiClient: Client,
    private val objectMapper: ObjectMapper,
    private val vertexAiProperties: VertexAiProperties,
) : GeminiClassifier {
    override fun classifyAndRecap(photos: List<PhotoRef>): List<ThemeClassification> {
        val photoAliases = GeminiPhotoAliases.from(photos)
        val parts =
            photos.map { Part.fromUri(it.gcsUri, it.mimeType) } +
                Part.fromText(GeminiPrompts.themeClassification(photoAliases.aliases))
        val content = Content.fromParts(*parts.toTypedArray())

        val httpOptions =
            HttpOptions
                .builder()
                .timeout(vertexAiProperties.classifyTimeoutMs.toInt())
                .retryOptions(
                    HttpRetryOptions
                        .builder()
                        .attempts(2)
                        .httpStatusCodes(listOf(429, 500, 502, 503, 504))
                        .build(),
                ).build()

        val config =
            GenerateContentConfig
                .builder()
                .responseMimeType("application/json")
                .responseSchema(classificationResponseSchema())
                .httpOptions(httpOptions)
                .build()

        val response = genAiClient.models.generateContent(MODEL, content, config)
        val rawThemes = objectMapper.readValue(response.text(), Array<GeminiThemeResponse>::class.java).toList()

        val inputPhotoIds = photos.map { it.photoId }.toSet()
        val classifications = toClassifications(rawThemes, photoAliases)
        if (classifications.isNotEmpty()) {
            ThemeClassificationValidator.validate(classifications, inputPhotoIds)
        }
        return classifications
    }

    override fun regenerateSticker(
        photos: List<PhotoRef>,
        previousSourcePhotoId: UUID,
    ): StickerRegenerationTarget {
        val photoAliases = GeminiPhotoAliases.from(photos)
        val parts =
            photos.map { Part.fromUri(it.gcsUri, it.mimeType) } +
                Part.fromText(GeminiPrompts.stickerRegeneration(photoAliases.aliases, photoAliases.aliasFor(previousSourcePhotoId)))
        val content = Content.fromParts(*parts.toTypedArray())

        val httpOptions =
            HttpOptions
                .builder()
                .timeout(vertexAiProperties.classifyTimeoutMs.toInt())
                .retryOptions(
                    HttpRetryOptions
                        .builder()
                        .attempts(2)
                        .httpStatusCodes(listOf(429, 500, 502, 503, 504))
                        .build(),
                ).build()

        val config =
            GenerateContentConfig
                .builder()
                .responseMimeType("application/json")
                .responseSchema(stickerResponseSchema())
                .httpOptions(httpOptions)
                .build()

        val response = genAiClient.models.generateContent(MODEL, content, config)
        val rawSticker = objectMapper.readValue(response.text(), GeminiStickerResponse::class.java)

        val inputPhotoIds = photos.map { it.photoId }.toSet()
        return toRegenerationTarget(rawSticker, photoAliases, inputPhotoIds)
    }

    companion object {
        private const val MODEL = "gemini-2.5-flash"
        private const val DEFAULT_MAIN_COLOR = "#222222"
        private val MAIN_COLOR_PATTERN = Regex("^#[0-9A-Fa-f]{6}$")
        private val log = LoggerFactory.getLogger(VertexAiGeminiClassifier::class.java)

        private fun sanitizedMainColor(
            raw: String?,
            context: String,
        ): String =
            raw?.takeIf { MAIN_COLOR_PATTERN.matches(it) } ?: run {
                log.warn("Gemini가 유효하지 않은 mainColor를 반환해 기본값으로 대체합니다: context={}, mainColor={}", context, raw)
                DEFAULT_MAIN_COLOR
            }

        private fun sanitizedComments(
            raw: GeminiCommentsResponse?,
            context: String,
        ): List<ThemeComment> {
            val bubbles =
                raw?.speechBubbles.orEmpty().mapNotNull { bubble ->
                    val content = bubble.content
                    if (content.isNullOrBlank() || bubble.posX == null || bubble.posY == null) {
                        log.warn("Gemini가 유효하지 않은 speechBubble을 반환해 건너뜁니다: context={}, bubble={}", context, bubble)
                        null
                    } else {
                        ThemeComment(content = content, posX = bubble.posX, posY = bubble.posY)
                    }
                }
            val chips =
                raw?.keywordChips.orEmpty().mapNotNull { chip ->
                    chip.takeUnless(String::isBlank)?.let { ThemeComment(content = it, posX = null, posY = null) }
                }
            return bubbles + chips
        }

        internal fun toClassifications(
            rawThemes: List<GeminiThemeResponse>,
            photoAliases: GeminiPhotoAliases,
        ): List<ThemeClassification> =
            rawThemes.mapIndexedNotNull { index, theme ->
                theme.toDomainOrNull(index, photoAliases)
            }

        internal fun toRegenerationTarget(
            rawSticker: GeminiStickerResponse,
            photoAliases: GeminiPhotoAliases,
            inputPhotoIds: Set<UUID>,
        ): StickerRegenerationTarget {
            val sourcePhotoId =
                photoAliases.photoId(rawSticker.sourcePhotoId)
                    ?: throw BusinessException(
                        AnalysisErrorCode.INVALID_GEMINI_RESPONSE,
                        message = "sticker.sourcePhotoId(${rawSticker.sourcePhotoId})가 입력 사진 alias 목록에 없습니다.",
                    )
            validateRegeneration(rawSticker, sourcePhotoId, inputPhotoIds)
            return StickerRegenerationTarget(
                stickerTargetSubject = rawSticker.targetSubject,
                stickerSourcePhotoId = sourcePhotoId,
                stickerMainColor = sanitizedMainColor(rawSticker.mainColor, "regenerate"),
            )
        }

        private fun validateRegeneration(
            sticker: GeminiStickerResponse,
            sourcePhotoId: UUID,
            inputPhotoIds: Set<UUID>,
        ) {
            if (sticker.targetSubject.isBlank()) {
                throw BusinessException(
                    AnalysisErrorCode.INVALID_GEMINI_RESPONSE,
                    message = "sticker.targetSubject가 비어있습니다.",
                )
            }
            if (sourcePhotoId !in inputPhotoIds) {
                throw BusinessException(
                    AnalysisErrorCode.INVALID_GEMINI_RESPONSE,
                    message = "sticker.sourcePhotoId(${sticker.sourcePhotoId})가 입력 사진 목록에 없습니다.",
                )
            }
        }

        private fun GeminiThemeResponse.toDomainOrNull(
            index: Int,
            photoAliases: GeminiPhotoAliases,
        ): ThemeClassification? =
            validCategorizedPhotoIds(index, photoAliases)?.let { categorizedPhotoIds ->
                validStickerSourcePhotoId(index, categorizedPhotoIds, photoAliases)?.let { stickerSourcePhotoId ->
                    ThemeClassification(
                        theme = theme,
                        categorizedPhotoIds = categorizedPhotoIds,
                        recap = RecapContent(badge = recap.badge, text = recap.text),
                        stickerTargetSubject = sticker.targetSubject,
                        stickerSourcePhotoId = stickerSourcePhotoId,
                        stickerMainColor = sanitizedMainColor(sticker.mainColor, theme),
                        comments = sanitizedComments(comments, theme),
                    )
                }
            }

        private fun GeminiThemeResponse.validCategorizedPhotoIds(
            index: Int,
            photoAliases: GeminiPhotoAliases,
        ): List<UUID>? {
            val categorizedPhotoIds =
                categorizedPhotoIds.mapNotNull { alias ->
                    photoAliases.photoId(alias).also { photoId ->
                        if (photoId == null) {
                            log.warn("Gemini가 알 수 없는 photo alias를 반환해 건너뜁니다: themeIndex={}, alias={}", index, alias)
                        }
                    }
                }
            if (categorizedPhotoIds.isEmpty()) {
                log.warn("Gemini 테마의 유효한 categorizedPhotoIds가 없어 테마를 건너뜁니다: themeIndex={}, theme={}", index, theme)
                return null
            }
            return categorizedPhotoIds
        }

        private fun GeminiThemeResponse.validStickerSourcePhotoId(
            index: Int,
            categorizedPhotoIds: List<UUID>,
            photoAliases: GeminiPhotoAliases,
        ): UUID? =
            photoAliases
                .photoId(sticker.sourcePhotoId)
                .also { stickerSourcePhotoId ->
                    if (stickerSourcePhotoId == null) {
                        log.warn(
                            "Gemini 테마의 sticker.sourcePhotoId alias를 찾을 수 없어 테마를 건너뜁니다: themeIndex={}, theme={}, alias={}",
                            index,
                            theme,
                            sticker.sourcePhotoId,
                        )
                    }
                }?.takeIf { stickerSourcePhotoId ->
                    val valid = stickerSourcePhotoId in categorizedPhotoIds
                    if (!valid) {
                        log.warn(
                            "Gemini 테마의 sticker.sourcePhotoId가 categorizedPhotoIds에 없어 테마를 건너뜁니다: themeIndex={}, theme={}, alias={}",
                            index,
                            theme,
                            sticker.sourcePhotoId,
                        )
                    }
                    valid
                }

        private val RECAP_SCHEMA =
            Schema
                .builder()
                .type(Type.Known.OBJECT)
                .properties(
                    mapOf(
                        "badge" to
                            Schema
                                .builder()
                                .type(Type.Known.STRING)
                                .build(),
                        "text" to
                            Schema
                                .builder()
                                .type(Type.Known.STRING)
                                .build(),
                    ),
                ).required("badge", "text")
                .build()

        internal fun stickerResponseSchema() =
            Schema
                .builder()
                .type(Type.Known.OBJECT)
                .properties(
                    mapOf(
                        "targetSubject" to
                            Schema
                                .builder()
                                .type(Type.Known.STRING)
                                .build(),
                        "sourcePhotoId" to
                            Schema
                                .builder()
                                .type(Type.Known.STRING)
                                .build(),
                        "mainColor" to
                            Schema
                                .builder()
                                .type(Type.Known.STRING)
                                .build(),
                    ),
                ).required("targetSubject", "sourcePhotoId", "mainColor")
                .build()

        private val SPEECH_BUBBLE_SCHEMA =
            Schema
                .builder()
                .type(Type.Known.OBJECT)
                .properties(
                    mapOf(
                        "content" to
                            Schema
                                .builder()
                                .type(Type.Known.STRING)
                                .build(),
                        "posX" to
                            Schema
                                .builder()
                                .type(Type.Known.NUMBER)
                                .build(),
                        "posY" to
                            Schema
                                .builder()
                                .type(Type.Known.NUMBER)
                                .build(),
                    ),
                ).required("content", "posX", "posY")
                .build()

        private val COMMENTS_SCHEMA =
            Schema
                .builder()
                .type(Type.Known.OBJECT)
                .properties(
                    mapOf(
                        "speechBubbles" to
                            Schema
                                .builder()
                                .type(Type.Known.ARRAY)
                                .items(SPEECH_BUBBLE_SCHEMA)
                                .build(),
                        "keywordChips" to
                            Schema
                                .builder()
                                .type(Type.Known.ARRAY)
                                .items(Schema.builder().type(Type.Known.STRING))
                                .build(),
                    ),
                ).required("speechBubbles", "keywordChips")
                .build()

        private fun themeSchema() =
            Schema
                .builder()
                .type(Type.Known.OBJECT)
                .properties(
                    mapOf(
                        "theme" to
                            Schema
                                .builder()
                                .type(Type.Known.STRING)
                                .build(),
                        "categorizedPhotoIds" to
                            Schema
                                .builder()
                                .type(Type.Known.ARRAY)
                                .items(
                                    Schema
                                        .builder()
                                        .type(Type.Known.STRING),
                                ).build(),
                        "recap" to RECAP_SCHEMA,
                        "sticker" to stickerResponseSchema(),
                        "comments" to COMMENTS_SCHEMA,
                    ),
                ).required("theme", "categorizedPhotoIds", "recap", "sticker", "comments")
                .build()

        internal fun classificationResponseSchema() =
            Schema
                .builder()
                .type(Type.Known.ARRAY)
                .items(themeSchema())
                .build()
    }
}

internal class GeminiPhotoAliases private constructor(
    private val photoIdByAlias: Map<String, UUID>,
    private val aliasByPhotoId: Map<UUID, String>,
) {
    val aliases: List<String> = photoIdByAlias.keys.toList()

    fun photoId(alias: String): UUID? = photoIdByAlias[alias.trim()]

    fun aliasFor(photoId: UUID): String? = aliasByPhotoId[photoId]

    companion object {
        fun from(photos: List<PhotoRef>): GeminiPhotoAliases {
            val photoIdByAlias = linkedMapOf<String, UUID>()
            val aliasByPhotoId = linkedMapOf<UUID, String>()
            photos.forEachIndexed { index, photo ->
                val alias = "P%03d".format(index + 1)
                photoIdByAlias[alias] = photo.photoId
                aliasByPhotoId[photo.photoId] = alias
            }
            return GeminiPhotoAliases(photoIdByAlias, aliasByPhotoId)
        }
    }
}

internal data class GeminiThemeResponse(
    val theme: String,
    val categorizedPhotoIds: List<String>,
    val recap: GeminiRecapResponse,
    val sticker: GeminiStickerResponse,
    val comments: GeminiCommentsResponse?,
)

internal data class GeminiRecapResponse(
    val badge: String,
    val text: String,
)

internal data class GeminiStickerResponse(
    val targetSubject: String,
    val sourcePhotoId: String,
    val mainColor: String?,
)

internal data class GeminiCommentsResponse(
    val speechBubbles: List<GeminiSpeechBubbleResponse>?,
    val keywordChips: List<String>?,
)

internal data class GeminiSpeechBubbleResponse(
    val content: String?,
    val posX: Double?,
    val posY: Double?,
)
