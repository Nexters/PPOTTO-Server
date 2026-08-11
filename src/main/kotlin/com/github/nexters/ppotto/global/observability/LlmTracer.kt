package com.github.nexters.ppotto.global.observability

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode

object LlmTracer {
    private const val INSTRUMENTATION_NAME = "ppotto.llm"
    private const val SYSTEM = "gcp.vertex_ai"

    fun <T> trace(
        operation: LlmOperation,
        model: String,
        attributes: Map<String, String> = emptyMap(),
        block: (LlmSpanHandle) -> T,
    ): T {
        val span =
            GlobalOpenTelemetry
                .getTracer(INSTRUMENTATION_NAME)
                .spanBuilder("${operation.value} $model")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("gen_ai.system", SYSTEM)
                .setAttribute("gen_ai.operation.name", operation.value)
                .setAttribute("gen_ai.request.model", model)
                .apply { attributes.forEach { (key, value) -> setAttribute(key, value) } }
                .startSpan()
        val result = runCatching { span.makeCurrent().use { block(OtelLlmSpanHandle(span)) } }
        result.exceptionOrNull()?.let { cause ->
            span.recordException(cause)
            span.setStatus(StatusCode.ERROR)
        } ?: span.setStatus(StatusCode.OK)
        span.end()
        return result.getOrThrow()
    }

    private class OtelLlmSpanHandle(
        private val span: Span,
    ) : LlmSpanHandle {
        override fun setUsage(
            inputTokens: Int?,
            outputTokens: Int?,
            cachedTokens: Int?,
            totalTokens: Int?,
        ) {
            inputTokens?.let { span.setAttribute(INPUT_TOKENS, it.toLong()) }
            outputTokens?.let { span.setAttribute(OUTPUT_TOKENS, it.toLong()) }
            cachedTokens?.let { span.setAttribute(CACHED_TOKENS, it.toLong()) }
            totalTokens?.let { span.setAttribute(TOTAL_TOKENS, it.toLong()) }
        }

        override fun setResponseModel(model: String) {
            span.setAttribute(RESPONSE_MODEL, model)
        }

        override fun setFinishReasons(reasons: List<String>) {
            span.setAttribute(FINISH_REASONS, reasons)
        }

        override fun setAttribute(
            key: String,
            value: String,
        ) {
            span.setAttribute(key, value)
        }

        override fun setAttribute(
            key: String,
            value: Long,
        ) {
            span.setAttribute(key, value)
        }

        companion object {
            private val INPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.input_tokens")
            private val OUTPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.output_tokens")
            private val CACHED_TOKENS = AttributeKey.longKey("gen_ai.usage.cached_tokens")
            private val TOTAL_TOKENS = AttributeKey.longKey("gen_ai.usage.total_tokens")
            private val RESPONSE_MODEL = AttributeKey.stringKey("gen_ai.response.model")
            private val FINISH_REASONS = AttributeKey.stringArrayKey("gen_ai.response.finish_reasons")
        }
    }
}
