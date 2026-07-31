package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.AnalysisErrorCode
import com.github.nexters.ppotto.analysis.domain.StickerGenerator
import com.github.nexters.ppotto.global.config.VertexAiProperties
import com.github.nexters.ppotto.global.error.BusinessException
import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.HttpOptions
import com.google.genai.types.HttpRetryOptions
import com.google.genai.types.Part
import org.springframework.stereotype.Component

@Component
class VertexAiStickerGenerator(
    private val genAiClient: Client,
    private val vertexAiProperties: VertexAiProperties,
) : StickerGenerator {
    override fun generate(
        sourceGcsUri: String,
        sourceMimeType: String,
        targetSubject: String,
    ): ByteArray {
        val content =
            Content.fromParts(
                Part.fromUri(sourceGcsUri, sourceMimeType),
                Part.fromText("이 사진에서 '$targetSubject'만 남기고 배경을 제거해서 투명 배경 PNG 스티커로 만들어줘."),
            )

        val httpOptions =
            HttpOptions
                .builder()
                .timeout(vertexAiProperties.stickerGenerationTimeoutMs.toInt())
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
                .httpOptions(httpOptions)
                .build()

        val response = genAiClient.models.generateContent(MODEL, content, config)
        val inlineData =
            response
                .parts()
                ?.firstNotNullOfOrNull { it.inlineData().orElse(null) }
                ?: throw BusinessException(AnalysisErrorCode.INVALID_GEMINI_RESPONSE)

        val mimeType = inlineData.mimeType().orElse(null)
        if (mimeType != EXPECTED_MIME_TYPE) {
            throw BusinessException(
                AnalysisErrorCode.INVALID_GEMINI_RESPONSE,
                message = "스티커 생성 응답의 MIME 타입이 $EXPECTED_MIME_TYPE 이 아닙니다. (실제: $mimeType)",
            )
        }

        return inlineData.data().orElseThrow { BusinessException(AnalysisErrorCode.INVALID_GEMINI_RESPONSE) }
    }

    companion object {
        private const val MODEL = "gemini-2.5-flash-image"
        private const val EXPECTED_MIME_TYPE = "image/png"
    }
}
