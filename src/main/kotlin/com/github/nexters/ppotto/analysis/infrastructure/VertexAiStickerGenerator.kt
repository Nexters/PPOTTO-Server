package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.AnalysisErrorCode
import com.github.nexters.ppotto.analysis.domain.StickerGenerator
import com.github.nexters.ppotto.global.error.BusinessException
import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.Part
import org.springframework.stereotype.Component

@Component
class VertexAiStickerGenerator(
    private val genAiClient: Client,
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

        val response = genAiClient.models.generateContent(MODEL, content, null)
        val inlineData =
            response
                .parts()
                ?.firstNotNullOfOrNull { it.inlineData().orElse(null) }
                ?: throw BusinessException(AnalysisErrorCode.INVALID_GEMINI_RESPONSE)

        return inlineData.data().orElseThrow { BusinessException(AnalysisErrorCode.INVALID_GEMINI_RESPONSE) }
    }

    companion object {
        private const val MODEL = "gemini-2.5-flash-image"
    }
}
