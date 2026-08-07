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
                    Preserve the subject's natural appeal from the original photo, including its pose, expression, color, texture, and recognizable silhouette.
                    Make the cutout feel clean, polished, and visually appealing as a standalone sticker, but do not redraw, cartoonize, beautify unrealistically, or add new design elements.

                    Orientation: keep the subject's overall up-down axis exactly as gravity would place it in real life. This is only about that axis, not the subject's pose — a sitting, lying, or reclining pose is fine. Do not rotate, tilt, flip, or invert the image so the subject reads as sideways or upside down — for example, a potted plant must stand upright, not sideways or upside down, and a standing person must have feet down and head up, not appear to stand on the ceiling.

                    Output format: the image must be a PNG cropped tightly to the subject's silhouette, with a genuine alpha-transparent background — every pixel outside the cutout must have alpha=0. Do not fill that area with white, gray, black, or any solid-color pixels, and do not bake in a gray/white checkerboard pattern as real pixels (that checkerboard is only an image-editor convention for showing transparency, never actual output content). Do not place the subject on any backdrop, canvas, frame, border, outline, drop shadow, or decorative element, and do not leave a large rectangular area of transparent padding around it as if it still sits inside the original photo's frame — the result should read as the sticker cutout itself, not a photo with its background removed.
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
