package com.github.nexters.ppotto.global.observability

import com.google.genai.types.GenerateContentResponse
import kotlin.jvm.optionals.getOrNull

fun LlmSpanHandle.record(response: GenerateContentResponse) {
    response.usageMetadata().getOrNull()?.let { usage ->
        setUsage(
            inputTokens = usage.promptTokenCount().getOrNull(),
            outputTokens = usage.candidatesTokenCount().getOrNull(),
            cachedTokens = usage.cachedContentTokenCount().getOrNull(),
            totalTokens = usage.totalTokenCount().getOrNull(),
        )
    }
    response
        .modelVersion()
        .getOrNull()
        ?.let(::setResponseModel)
    response
        .candidates()
        .getOrNull()
        ?.mapNotNull { candidate ->
            candidate
                .finishReason()
                .getOrNull()
                ?.toString()
        }?.takeIf { it.isNotEmpty() }
        ?.let(::setFinishReasons)
}
