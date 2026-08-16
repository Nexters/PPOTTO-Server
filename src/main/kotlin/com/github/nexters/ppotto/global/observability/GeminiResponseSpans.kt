package com.github.nexters.ppotto.global.observability

import com.google.genai.types.Candidate
import com.google.genai.types.GenerateContentResponse
import com.google.genai.types.GenerateContentResponseUsageMetadata
import kotlin.jvm.optionals.getOrNull

fun LlmSpanHandle.recordResponse(response: GenerateContentResponse) {
    response
        .usageMetadata()
        .getOrNull()
        ?.let(::recordUsage)
    response
        .modelVersion()
        .getOrNull()
        ?.let(::setResponseModel)
    response
        .responseId()
        .getOrNull()
        ?.let(::setResponseId)
    response
        .candidates()
        .getOrNull()
        ?.let(::recordCandidates)
}

private fun LlmSpanHandle.recordCandidates(candidates: List<Candidate>) {
    candidates
        .mapNotNull { candidate ->
            candidate
                .finishReason()
                .getOrNull()
                ?.toString()
        }.distinct()
        .takeIf { it.isNotEmpty() }
        ?.let(::setFinishReasons)
    candidates
        .mapNotNull { candidate ->
            candidate
                .content()
                .getOrNull()
                ?.toLlmMessage(LlmRole.ASSISTANT)
        }.takeIf { it.isNotEmpty() }
        ?.let(::setOutputMessages)
}

private fun LlmSpanHandle.recordUsage(usage: GenerateContentResponseUsageMetadata) {
    val reasoningOutputTokens = usage.thoughtsTokenCount().getOrNull()
    setUsage(
        inputTokens =
            sumOrNull(
                usage.promptTokenCount().getOrNull(),
                usage.toolUsePromptTokenCount().getOrNull(),
            ),
        outputTokens =
            sumOrNull(
                usage.candidatesTokenCount().getOrNull(),
                reasoningOutputTokens,
            ),
        cachedInputTokens = usage.cachedContentTokenCount().getOrNull(),
        reasoningOutputTokens = reasoningOutputTokens,
        totalTokens = usage.totalTokenCount().getOrNull(),
    )
}

private fun sumOrNull(
    first: Int?,
    second: Int?,
): Int? =
    when {
        first == null && second == null -> null
        else -> (first ?: 0) + (second ?: 0)
    }
