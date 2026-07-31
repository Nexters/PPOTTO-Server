package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.GeminiClassifier
import com.github.nexters.ppotto.analysis.domain.PhotoRef
import com.github.nexters.ppotto.analysis.domain.RecapContent
import com.github.nexters.ppotto.analysis.domain.ThemeClassification
import com.github.nexters.ppotto.analysis.domain.ThemeClassificationValidator
import com.github.nexters.ppotto.global.config.VertexAiProperties
import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.HttpOptions
import com.google.genai.types.HttpRetryOptions
import com.google.genai.types.Part
import com.google.genai.types.Schema
import com.google.genai.types.Type
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
                Part.fromText(buildPrompt(photos.map { it.photoId }))
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
                .responseSchema(RESPONSE_SCHEMA)
                .httpOptions(httpOptions)
                .build()

        val response = genAiClient.models.generateContent(MODEL, content, config)
        val rawThemes = objectMapper.readValue(response.text(), Array<GeminiThemeResponse>::class.java).toList()

        val inputPhotoIds = photos.map { it.photoId }.toSet()
        val classifications = rawThemes.map { it.toDomain() }
        ThemeClassificationValidator.validate(classifications, inputPhotoIds)
        return classifications
    }

    private fun buildPrompt(photoIds: List<UUID>): String =
        """
        아래에 첨부된 사진들을 최대 ${ThemeClassificationValidator.MAX_THEME_COUNT} 개의 테마로 분류해줘. 각 사진은 정확히 하나의 테마에만 속해야 하고,
        어느 테마에도 어울리지 않는 사진은 결과에서 제외해도 돼.

        사진 목록(순서대로): ${photoIds.joinToString(", ")}

        각 테마에 대해 다음을 생성해줘:
        - theme: 테마 이름 (한국어)
        - categorizedPhotoIds: 이 테마로 분류된 사진 id 목록 (위 목록에 있는 값만 사용)
        - recap.badge: 8자 내외의 짧은 뱃지 문구 (한국어)
        - recap.text: 1~2문장의 리캡 문구 (한국어)
        - sticker.targetSubject: 스티커로 만들 피사체에 대한 구체적인 설명 (한국어)
        - sticker.sourcePhotoId: 스티커의 원본으로 쓸 사진 id. 반드시 이 테마의 categorizedPhotoIds 안에 있는 값이어야 함.

        스티커 원본 사진과 피사체는 사용자가 직관적으로 예쁘다, 멋지다, 귀엽다, 인상적이다고 느낄 만한 것을 골라줘.
        좋은 스티커 후보를 고르는 기준:
        - 피사체가 선명하고 충분히 크며, 조명과 색감이 좋고, 구도나 포즈가 매력적임
        - 배경을 제거해도 피사체의 실루엣과 의미가 독립적으로 잘 살아남음
        - 테마를 상징적으로 잘 보여주고, 감정이나 개성이 잘 드러남
        피해야 할 후보:
        - 흐리거나 어둡거나 너무 작아서 누끼 후 볼품없어지는 피사체
        - 여러 물체가 복잡하게 겹쳐 경계가 애매한 피사체
        - 배경이 핵심이라 누끼를 따면 의미가 약해지는 장면
        targetSubject는 누끼 대상이 정확히 드러나도록 "빨간 옷을 입고 웃는 사람", "책상 위의 노란 캐릭터 인형"처럼 구체적으로 작성해줘.

        모든 텍스트 출력은 한국어로 작성해줘.
        """.trimIndent()

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
            )
    }

    private data class GeminiRecapResponse(
        val badge: String,
        val text: String,
    )

    private data class GeminiStickerResponse(
        val targetSubject: String,
        val sourcePhotoId: UUID,
    )

    companion object {
        private const val MODEL = "gemini-2.5-flash"

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

        private val STICKER_SCHEMA =
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
                    ),
                ).required("targetSubject", "sourcePhotoId")
                .build()

        private val THEME_SCHEMA =
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
                                .items(Schema.builder().type(Type.Known.STRING))
                                .build(),
                        "recap" to RECAP_SCHEMA,
                        "sticker" to STICKER_SCHEMA,
                    ),
                ).required("theme", "categorizedPhotoIds", "recap", "sticker")
                .build()

        private val RESPONSE_SCHEMA =
            Schema
                .builder()
                .type(Type.Known.ARRAY)
                .items(THEME_SCHEMA)
                .build()
    }
}
