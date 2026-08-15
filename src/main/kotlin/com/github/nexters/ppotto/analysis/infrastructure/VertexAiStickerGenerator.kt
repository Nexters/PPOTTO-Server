package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.AnalysisErrorCode
import com.github.nexters.ppotto.analysis.domain.StickerGenerator
import com.github.nexters.ppotto.global.config.VertexAiProperties
import com.github.nexters.ppotto.global.error.BusinessException
import com.github.nexters.ppotto.global.observability.LlmOperation
import com.github.nexters.ppotto.global.observability.LlmTracer
import com.github.nexters.ppotto.global.observability.record
import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.HttpOptions
import com.google.genai.types.HttpRetryOptions
import com.google.genai.types.Part
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class VertexAiStickerGenerator(
    private val genAiClient: Client,
    private val vertexAiProperties: VertexAiProperties,
    private val pixianBackgroundRemover: PixianBackgroundRemover,
    private val stickerImageCropper: StickerImageCropper,
) : StickerGenerator {
    override fun generate(
        sourceGcsUri: String,
        sourceMimeType: String,
        targetSubject: String,
    ): ByteArray {
        val content =
            Content.fromParts(
                Part.fromUri(sourceGcsUri, sourceMimeType),
                Part.fromText(GeminiPrompts.stickerCutout(targetSubject)),
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

        val response =
            LlmTracer.trace(LlmOperation.STICKER_GENERATION, MODEL) { span ->
                genAiClient.models
                    .generateContent(MODEL, content, config)
                    .also { span.record(it) }
            }
        val inlineData =
            response
                .parts()
                ?.firstNotNullOfOrNull { it.inlineData().orElse(null) }
        if (inlineData == null) {
            val refusalText =
                response
                    .parts()
                    ?.mapNotNull { it.text().orElse(null) }
                    ?.joinToString(" ")
            log.warn("sticker generation returned no image data: targetSubject={}, modelText={}", targetSubject, refusalText)
            throw BusinessException(AnalysisErrorCode.INVALID_GEMINI_RESPONSE)
        }

        val mimeType = inlineData.mimeType().orElse(null)
        if (mimeType != EXPECTED_MIME_TYPE) {
            throw BusinessException(
                AnalysisErrorCode.INVALID_GEMINI_RESPONSE,
                message = "스티커 생성 응답의 MIME 타입이 $EXPECTED_MIME_TYPE 이 아닙니다. (실제: $mimeType)",
            )
        }

        val rawBytes = inlineData.data().orElseThrow { BusinessException(AnalysisErrorCode.INVALID_GEMINI_RESPONSE) }
        val removedBackgroundBytes = pixianBackgroundRemover.removeBackground(rawBytes)
        return stickerImageCropper.cropTransparentPadding(removedBackgroundBytes)
    }

    companion object {
        private val log = LoggerFactory.getLogger(VertexAiStickerGenerator::class.java)
        private const val MODEL = "gemini-2.5-flash-image"
        private const val EXPECTED_MIME_TYPE = "image/png"
    }
}
