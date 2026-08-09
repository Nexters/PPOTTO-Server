package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.AnalysisErrorCode
import com.github.nexters.ppotto.analysis.domain.GeminiClassifier
import com.github.nexters.ppotto.analysis.domain.PhotoRef
import com.github.nexters.ppotto.analysis.domain.RecapContent
import com.github.nexters.ppotto.analysis.domain.StickerRegenerationTarget
import com.github.nexters.ppotto.analysis.domain.ThemeClassification
import com.github.nexters.ppotto.analysis.domain.ThemeClassificationValidator
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
        val parts =
            photos.map { Part.fromUri(it.gcsUri, it.mimeType) } +
                Part.fromText(GeminiPrompts.themeClassification(photos.map { it.photoId }))
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

        val photoIdStrings = photos.map { it.photoId.toString() }
        val config =
            GenerateContentConfig
                .builder()
                .responseMimeType("application/json")
                .responseSchema(responseSchema(photoIdStrings))
                .httpOptions(httpOptions)
                .build()

        val response = genAiClient.models.generateContent(MODEL, content, config)
        val rawThemes = objectMapper.readValue(response.text(), Array<GeminiThemeResponse>::class.java).toList()

        val inputPhotoIds = photos.map { it.photoId }.toSet()
        val classifications = rawThemes.map { it.toDomain() }
        ThemeClassificationValidator.validate(classifications, inputPhotoIds)
        return classifications
    }

    override fun regenerateSticker(
        photos: List<PhotoRef>,
        previousSourcePhotoId: UUID,
    ): StickerRegenerationTarget {
        val parts =
            photos.map { Part.fromUri(it.gcsUri, it.mimeType) } +
                Part.fromText(GeminiPrompts.stickerRegeneration(photos.map { it.photoId }, previousSourcePhotoId))
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

        val photoIdStrings = photos.map { it.photoId.toString() }
        val config =
            GenerateContentConfig
                .builder()
                .responseMimeType("application/json")
                .responseSchema(stickerSchema(photoIdStrings))
                .httpOptions(httpOptions)
                .build()

        val response = genAiClient.models.generateContent(MODEL, content, config)
        val rawSticker = objectMapper.readValue(response.text(), GeminiStickerResponse::class.java)

        val inputPhotoIds = photos.map { it.photoId }.toSet()
        validateRegeneration(rawSticker, inputPhotoIds)

        return StickerRegenerationTarget(
            stickerTargetSubject = rawSticker.targetSubject,
            stickerSourcePhotoId = rawSticker.sourcePhotoId,
            stickerMainColor = sanitizedMainColor(rawSticker.mainColor, "regenerate"),
        )
    }

    private fun validateRegeneration(
        sticker: GeminiStickerResponse,
        inputPhotoIds: Set<UUID>,
    ) {
        if (sticker.targetSubject.isBlank()) {
            throw BusinessException(
                AnalysisErrorCode.INVALID_GEMINI_RESPONSE,
                message = "sticker.targetSubject가 비어있습니다.",
            )
        }
        if (sticker.sourcePhotoId !in inputPhotoIds) {
            throw BusinessException(
                AnalysisErrorCode.INVALID_GEMINI_RESPONSE,
                message = "sticker.sourcePhotoId(${sticker.sourcePhotoId})가 입력 사진 목록에 없습니다.",
            )
        }
    }

    private data class GeminiThemeResponse(
        val theme: String,
        val categorizedPhotoIds: List<UUID>,
        val recap: GeminiRecapResponse,
        val sticker: GeminiStickerResponse,
    ) {
        fun toDomain(): ThemeClassification =
            ThemeClassification(
                theme = theme,
                categorizedPhotoIds = categorizedPhotoIds,
                recap = RecapContent(badge = recap.badge, text = recap.text),
                stickerTargetSubject = sticker.targetSubject,
                stickerSourcePhotoId = sticker.sourcePhotoId,
                stickerMainColor = sanitizedMainColor(sticker.mainColor, theme),
            )
    }

    private data class GeminiRecapResponse(
        val badge: String,
        val text: String,
    )

    private data class GeminiStickerResponse(
        val targetSubject: String,
        val sourcePhotoId: UUID,
        val mainColor: String?,
    )

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

        private fun stickerSchema(validPhotoIds: List<String>) =
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
                                .enum_(validPhotoIds)
                                .build(),
                        "mainColor" to
                            Schema
                                .builder()
                                .type(Type.Known.STRING)
                                .pattern("^#[0-9A-Fa-f]{6}$")
                                .build(),
                    ),
                ).required("targetSubject", "sourcePhotoId", "mainColor")
                .build()

        private fun themeSchema(validPhotoIds: List<String>) =
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
                                        .type(Type.Known.STRING)
                                        .enum_(validPhotoIds),
                                ).build(),
                        "recap" to RECAP_SCHEMA,
                        "sticker" to stickerSchema(validPhotoIds),
                    ),
                ).required("theme", "categorizedPhotoIds", "recap", "sticker")
                .build()

        private fun responseSchema(validPhotoIds: List<String>) =
            Schema
                .builder()
                .type(Type.Known.ARRAY)
                .items(themeSchema(validPhotoIds))
                .build()
    }
}
