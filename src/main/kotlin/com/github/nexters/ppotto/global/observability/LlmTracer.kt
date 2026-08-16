package com.github.nexters.ppotto.global.observability

import io.sentry.ISpan
import io.sentry.Sentry
import io.sentry.SpanStatus

object LlmTracer {
    fun <T> trace(
        pipeline: LlmPipeline,
        model: String,
        attributes: Map<String, String> = emptyMap(),
        block: (LlmSpanHandle) -> T,
    ): T {
        val span = startSpan("$OPERATION_NAME $model")
        span.setData(OPERATION_NAME_KEY, OPERATION_NAME)
        span.setData(PROVIDER_NAME_KEY, PROVIDER)
        span.setData(SYSTEM_KEY, PROVIDER)
        span.setData(REQUEST_MODEL_KEY, model)
        span.setData(PIPELINE_NAME_KEY, pipeline.value)
        attributes.forEach { (key, value) -> span.setData(key, value) }

        val result = runCatching { span.makeCurrent().use { block(SentryLlmSpanHandle(span)) } }
        result.exceptionOrNull()?.let { cause ->
            span.throwable = cause
            span.finish(SpanStatus.INTERNAL_ERROR)
        } ?: span.finish(SpanStatus.OK)
        return result.getOrThrow()
    }

    private fun startSpan(name: String): ISpan =
        Sentry.getSpan()?.startChild(SPAN_OP, name)
            ?: Sentry.startTransaction(name, SPAN_OP)

    private class SentryLlmSpanHandle(
        private val span: ISpan,
    ) : LlmSpanHandle {
        override fun setUsage(
            inputTokens: Int?,
            outputTokens: Int?,
            cachedInputTokens: Int?,
            reasoningOutputTokens: Int?,
            totalTokens: Int?,
        ) {
            inputTokens?.let { span.setData(INPUT_TOKENS_KEY, it) }
            outputTokens?.let { span.setData(OUTPUT_TOKENS_KEY, it) }
            cachedInputTokens?.let { span.setData(CACHE_READ_INPUT_TOKENS_KEY, it) }
            reasoningOutputTokens?.let { span.setData(REASONING_OUTPUT_TOKENS_KEY, it) }
            totalTokens?.let { span.setData(TOTAL_TOKENS_KEY, it) }
        }

        override fun setResponseModel(model: String) {
            span.setData(RESPONSE_MODEL_KEY, model)
        }

        override fun setResponseId(responseId: String) {
            span.setData(RESPONSE_ID_KEY, responseId)
        }

        override fun setFinishReasons(reasons: List<String>) {
            span.setData(FINISH_REASONS_KEY, reasons.joinToString(FINISH_REASON_SEPARATOR))
        }

        override fun setAttribute(
            key: String,
            value: String,
        ) {
            span.setData(key, value)
        }

        override fun setAttribute(
            key: String,
            value: Long,
        ) {
            span.setData(key, value)
        }
    }
}

private const val OPERATION_NAME = "chat"
private const val SPAN_OP = "gen_ai.$OPERATION_NAME"
private const val PROVIDER = "gcp.gemini"
private const val FINISH_REASON_SEPARATOR = ","

private const val OPERATION_NAME_KEY = "gen_ai.operation.name"
private const val PROVIDER_NAME_KEY = "gen_ai.provider.name"
private const val SYSTEM_KEY = "gen_ai.system"
private const val PIPELINE_NAME_KEY = "gen_ai.pipeline.name"
private const val REQUEST_MODEL_KEY = "gen_ai.request.model"
private const val RESPONSE_MODEL_KEY = "gen_ai.response.model"
private const val RESPONSE_ID_KEY = "gen_ai.response.id"
private const val FINISH_REASONS_KEY = "gen_ai.response.finish_reasons"
private const val INPUT_TOKENS_KEY = "gen_ai.usage.input_tokens"
private const val OUTPUT_TOKENS_KEY = "gen_ai.usage.output_tokens"
private const val CACHE_READ_INPUT_TOKENS_KEY = "gen_ai.usage.cache_read.input_tokens"
private const val REASONING_OUTPUT_TOKENS_KEY = "gen_ai.usage.reasoning.output_tokens"
private const val TOTAL_TOKENS_KEY = "gen_ai.usage.total_tokens"
