package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.config.VertexAiProperties
import com.github.nexters.ppotto.analysis.domain.AnalysisErrorCode
import com.github.nexters.ppotto.analysis.domain.PhotoRef
import com.github.nexters.ppotto.analysis.domain.RecapContent
import com.github.nexters.ppotto.analysis.domain.StickerRegenerationTarget
import com.github.nexters.ppotto.analysis.domain.StickerSubjectVerification
import com.github.nexters.ppotto.analysis.domain.ThemeClassification
import com.github.nexters.ppotto.analysis.domain.ThemeClassificationValidator
import com.github.nexters.ppotto.analysis.domain.ThemeClassifier
import com.github.nexters.ppotto.analysis.domain.ThemeComment
import com.github.nexters.ppotto.analysis.domain.cappedToMaxThemeCount
import com.github.nexters.ppotto.analysis.domain.repairCrossThemeDuplicates
import com.github.nexters.ppotto.global.error.BusinessException
import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.global.observability.LlmPipeline
import com.github.nexters.ppotto.global.observability.LlmTracer
import com.github.nexters.ppotto.global.observability.recordRequest
import com.github.nexters.ppotto.global.observability.recordResponse
import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.HttpOptions
import com.google.genai.types.HttpRetryOptions
import com.google.genai.types.Part
import com.google.genai.types.Schema
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class VertexAiGeminiClassifier(
    private val genAiClient: Client,
    private val objectMapper: ObjectMapper,
    private val vertexAiProperties: VertexAiProperties,
) : ThemeClassifier {
    override fun classifyAndRecap(photos: List<PhotoRef>): List<ThemeClassification> {
        val photoAliases = GeminiPhotoAliases.from(photos)
        val rawThemes =
            generate<Array<GeminiThemeResponse?>>(
                pipeline = LlmPipeline.PHOTO_CLASSIFICATION,
                parts = photos.toParts() + Part.fromText(GeminiPrompts.themeClassification(photoAliases.aliases)),
                responseSchema = VertexAiGeminiSchemas.CLASSIFICATION_RESPONSE_SCHEMA,
                timeoutMs = vertexAiProperties.classifyTimeoutMs,
                photoCount = photos.size,
                systemInstruction = Content.fromParts(Part.fromText(GeminiPrompts.COPY_VOICE)),
            ).toList()

        val repair = toClassifications(rawThemes, photoAliases).cappedToMaxThemeCount().repairCrossThemeDuplicates()
        if (repair.removedPhotoCount > 0) {
            log.warn("여러 테마에 중복 분류된 사진 {}장을 한 테마에만 남겼습니다.", repair.removedPhotoCount)
        }

        return repair.classifications
            .also { ThemeClassificationValidator.validate(it, photos.map { photo -> photo.photoId }.toSet()) }
    }

    override fun regenerateSticker(
        photos: List<PhotoRef>,
        previousSourcePhotoId: PhotoId,
    ): StickerRegenerationTarget {
        val photoAliases = GeminiPhotoAliases.from(photos)
        val prompt = GeminiPrompts.stickerRegeneration(photoAliases.aliases, photoAliases.aliasFor(previousSourcePhotoId))
        return generate<GeminiStickerResponse>(
            pipeline = LlmPipeline.STICKER_REGENERATION,
            parts = photos.toParts() + Part.fromText(prompt),
            responseSchema = VertexAiGeminiSchemas.STICKER_RESPONSE_SCHEMA,
            timeoutMs = vertexAiProperties.classifyTimeoutMs,
            photoCount = photos.size,
        ).let { toRegenerationTarget(it, photoAliases, photos.map { photo -> photo.photoId }.toSet()) }
    }

    override fun verifyStickerSubject(
        photo: PhotoRef,
        targetSubject: String,
    ): StickerSubjectVerification? =
        generate<GeminiSubjectVerificationResponse>(
            pipeline = LlmPipeline.STICKER_SUBJECT_VERIFICATION,
            parts = listOf(photo).toParts() + Part.fromText(GeminiPrompts.verifyStickerSubject(targetSubject)),
            responseSchema = VertexAiGeminiSchemas.VERIFICATION_RESPONSE_SCHEMA,
            timeoutMs = vertexAiProperties.verifyTimeoutMs,
            photoCount = 1,
        ).let { toVerification(it) }

    private inline fun <reified T> generate(
        pipeline: LlmPipeline,
        parts: List<Part>,
        responseSchema: Schema,
        timeoutMs: Long,
        photoCount: Int,
        systemInstruction: Content? = null,
    ): T {
        val content = Content.fromParts(*parts.toTypedArray())
        val configBuilder =
            GenerateContentConfig
                .builder()
                .responseMimeType("application/json")
                .responseSchema(responseSchema)
                .httpOptions(buildHttpOptions(timeoutMs))
        systemInstruction?.let(configBuilder::systemInstruction)
        val config = configBuilder.build()
        val response =
            runCatching {
                LlmTracer.trace(pipeline, MODEL, attributes = mapOf(ATTR_PHOTO_COUNT to photoCount.toString())) { span ->
                    span.recordRequest(content, config)
                    genAiClient.models
                        .generateContent(MODEL, content, config)
                        .also { span.recordResponse(it) }
                }
            }.getOrElse {
                throw if (pipeline == LlmPipeline.PHOTO_CLASSIFICATION) {
                    BusinessException(AnalysisErrorCode.CLASSIFICATION_FAILED, cause = it)
                } else {
                    it
                }
            }
        return runCatching { checkNotNull(objectMapper.readValue(response.text(), T::class.java)) }
            .getOrElse { throw BusinessException(AnalysisErrorCode.INVALID_GEMINI_RESPONSE, cause = it) }
    }

    private fun List<PhotoRef>.toParts(): List<Part> = map { Part.fromUri(it.sourceUri, it.mimeType) }

    companion object {
        private const val MODEL = "gemini-2.5-flash"
        private const val ATTR_PHOTO_COUNT = "ppotto.llm.photo_count"
        private const val DEFAULT_MAIN_COLOR = "#222222"
        private const val RETRY_ATTEMPTS = 5
        private val MAIN_COLOR_PATTERN = Regex("^#[0-9A-Fa-f]{6}$")
        private val log = LoggerFactory.getLogger(VertexAiGeminiClassifier::class.java)

        private fun buildHttpOptions(timeoutMs: Long): HttpOptions =
            HttpOptions
                .builder()
                .timeout(timeoutMs.toInt())
                .retryOptions(
                    HttpRetryOptions
                        .builder()
                        .attempts(RETRY_ATTEMPTS)
                        .httpStatusCodes(listOf(408, 429, 500, 502, 503, 504))
                        .build(),
                ).build()

        private fun sanitizedMainColor(
            raw: String?,
            context: String,
        ): String {
            if (raw != null && MAIN_COLOR_PATTERN.matches(raw)) return raw

            log.warn("Gemini가 유효하지 않은 mainColor를 반환해 기본값으로 대체합니다: context={}, mainColor={}", context, raw)
            return DEFAULT_MAIN_COLOR
        }

        private fun cappedText(
            raw: String,
            max: Int,
            field: String,
            context: String,
        ): String {
            if (raw.length <= max) return raw

            log.warn("Gemini가 {} 길이 상한을 넘겨 잘라냅니다: context={}, length={}", field, context, raw.length)
            return raw
                .take(max)
                .trimEnd { it.isHighSurrogate() }
                .trimEnd()
        }

        private fun sanitizedComments(
            raw: GeminiCommentsResponse?,
            context: String,
        ): List<ThemeComment> {
            val bubbles =
                raw?.speechBubbles.orEmpty().mapNotNull { bubble ->
                    val content = bubble.content?.takeIf { it.isNotBlank() && it.length <= ThemeComment.MAX_BUBBLE_LENGTH }
                    if (content == null || bubble.posX == null || bubble.posY == null) {
                        log.warn("Gemini가 유효하지 않은 speechBubble을 반환해 건너뜁니다: context={}, bubble={}", context, bubble)
                        null
                    } else {
                        ThemeComment(content = content, posX = bubble.posX, posY = bubble.posY)
                    }
                }
            val chips =
                raw?.keywordChips.orEmpty().mapNotNull { chip ->
                    chip
                        .withoutHashtagPrefix()
                        .takeUnless { it.isBlank() || it.length > ThemeComment.MAX_CHIP_LENGTH }
                        ?.let { ThemeComment(content = it, posX = null, posY = null) }
                }
            return bubbles + chips
        }

        internal fun toClassifications(
            rawThemes: List<GeminiThemeResponse?>,
            photoAliases: GeminiPhotoAliases,
        ): List<ThemeClassification> =
            rawThemes.mapIndexedNotNull { index, theme ->
                val validTheme = theme ?: throw BusinessException(AnalysisErrorCode.INVALID_GEMINI_RESPONSE)
                validTheme.toDomainOrNull(index, photoAliases)
            }

        internal fun toRegenerationTarget(
            rawSticker: GeminiStickerResponse,
            photoAliases: GeminiPhotoAliases,
            inputPhotoIds: Set<PhotoId>,
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

        internal fun toVerification(raw: GeminiSubjectVerificationResponse): StickerSubjectVerification? {
            if (raw.subjectPresent == false) return null
            val targetSubject = raw.targetSubject
            if (raw.subjectPresent != true || targetSubject.isNullOrBlank()) {
                throw BusinessException(AnalysisErrorCode.INVALID_GEMINI_RESPONSE)
            }
            return StickerSubjectVerification(targetSubject = targetSubject, mainColor = sanitizedMainColor(raw.mainColor, "verify"))
        }

        private fun validateRegeneration(
            sticker: GeminiStickerResponse,
            sourcePhotoId: PhotoId,
            inputPhotoIds: Set<PhotoId>,
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
        ): ThemeClassification? {
            val categorizedPhotoIds = validCategorizedPhotoIds(index, photoAliases) ?: return null
            val stickerSourcePhotoId = validStickerSourcePhotoId(index, categorizedPhotoIds, photoAliases) ?: return null

            return ThemeClassification(
                theme = theme,
                categorizedPhotoIds = categorizedPhotoIds,
                recap =
                    RecapContent(
                        badge = cappedText(recap.badge, RecapContent.MAX_BADGE_LENGTH, "recap.badge", theme),
                        text = cappedText(recap.text, RecapContent.MAX_TEXT_LENGTH, "recap.text", theme),
                    ),
                stickerTargetSubject = sticker.targetSubject,
                stickerSourcePhotoId = stickerSourcePhotoId,
                stickerMainColor = sanitizedMainColor(sticker.mainColor, theme),
                comments = sanitizedComments(comments, theme),
            )
        }

        private fun GeminiThemeResponse.validCategorizedPhotoIds(
            index: Int,
            photoAliases: GeminiPhotoAliases,
        ): List<PhotoId>? {
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
            categorizedPhotoIds: List<PhotoId>,
            photoAliases: GeminiPhotoAliases,
        ): PhotoId? {
            val stickerSourcePhotoId = photoAliases.photoId(sticker.sourcePhotoId)
            if (stickerSourcePhotoId != null && stickerSourcePhotoId in categorizedPhotoIds) return stickerSourcePhotoId

            log.warn(
                "Gemini 테마의 sticker.sourcePhotoId를 쓸 수 없어 테마를 건너뜁니다: themeIndex={}, theme={}, alias={}, aliasFound={}",
                index,
                theme,
                sticker.sourcePhotoId,
                stickerSourcePhotoId != null,
            )
            return null
        }
    }
}

private fun String.withoutHashtagPrefix(): String = trimStart('#').trim()

internal class GeminiPhotoAliases private constructor(
    private val photoIdByAlias: Map<String, PhotoId>,
) {
    private val aliasByPhotoId: Map<PhotoId, String> = photoIdByAlias.entries.associate { (alias, photoId) -> photoId to alias }

    val aliases: List<String> = photoIdByAlias.keys.toList()

    fun photoId(alias: String): PhotoId? = photoIdByAlias[alias.trim()]

    fun aliasFor(photoId: PhotoId): String? = aliasByPhotoId[photoId]

    companion object {
        fun from(photos: List<PhotoRef>): GeminiPhotoAliases =
            GeminiPhotoAliases(photos.mapIndexed { index, photo -> "P%03d".format(index + 1) to photo.photoId }.toMap())
    }
}

internal data class GeminiThemeResponse(
    val observedDetails: List<String>? = null,
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

internal data class GeminiSubjectVerificationResponse(
    val subjectPresent: Boolean?,
    val targetSubject: String?,
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
