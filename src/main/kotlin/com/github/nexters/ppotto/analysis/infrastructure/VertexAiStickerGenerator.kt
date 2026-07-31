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
                Part.fromText(
                    """
                    Create a sticker image by isolating only this subject from the photo: '$targetSubject'.
                    Preserve the subject's natural appeal from the original photo, including an attractive pose, expression, color, texture, and recognizable silhouette.
                    Make the cutout feel clean, polished, and visually appealing as a standalone sticker, but do not redraw, cartoonize, beautify unrealistically, or add new design elements.
                    The output must be a PNG with a fully transparent alpha-channel background.
                    Do not keep or generate any original background, white background, black background, solid-color background, checkerboard background, studio backdrop, shadow, outline, border, or decorative element.
                    Every pixel outside the cutout subject must be transparent, and the subject edge should be clean and natural.
                    """.trimIndent(),
                ),
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
