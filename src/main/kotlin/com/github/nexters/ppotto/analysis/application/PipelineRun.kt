package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.global.identifier.AnalysisId
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

class PipelineRun(
    val analysisId: AnalysisId,
) {
    var failedStep: String? = null
        private set

    fun <T> measured(
        step: String,
        block: () -> T,
    ): T {
        val startedAt = System.nanoTime()
        log.info("analysis pipeline step started: analysisId={}, step={}", analysisId, step)
        return runCatching(block)
            .onSuccess {
                log.info("analysis pipeline step completed: analysisId={}, step={}, elapsedMs={}", analysisId, step, elapsedMs(startedAt))
            }.onFailure {
                log.error("analysis pipeline step failed: analysisId={}, step={}, elapsedMs={}", analysisId, step, elapsedMs(startedAt), it)
                failedStep = failedStep ?: step
            }.getOrThrow()
    }

    fun <T> degrade(
        step: String,
        themeIndex: Int,
        theme: String,
        fallback: () -> T,
        block: () -> T,
    ): T {
        val startedAt = System.nanoTime()
        return runCatching(block).getOrElse {
            log.warn(
                "analysis pipeline step degraded: analysisId={}, step={}, themeIndex={}, theme={}, elapsedMs={}",
                analysisId,
                step,
                themeIndex,
                theme,
                elapsedMs(startedAt),
                it,
            )
            fallback()
        }
    }

    fun <T> measuredGeminiCall(
        operation: String,
        themeIndex: Int,
        theme: String,
        block: () -> T,
    ): T {
        val startedAt = System.nanoTime()
        log.info(
            "analysis pipeline gemini call started: analysisId={}, operation={}, themeIndex={}, theme={}, activeCount={}",
            analysisId,
            operation,
            themeIndex,
            theme,
            ACTIVE_GEMINI_CALL_COUNT.incrementAndGet(),
        )
        return try {
            block()
        } finally {
            log.info(
                "analysis pipeline gemini call finished: " +
                    "analysisId={}, operation={}, themeIndex={}, theme={}, activeCount={}, elapsedMs={}",
                analysisId,
                operation,
                themeIndex,
                theme,
                ACTIVE_GEMINI_CALL_COUNT.decrementAndGet(),
                elapsedMs(startedAt),
            )
        }
    }

    fun failureReason(failure: Throwable): String =
        "[${failedStep ?: UNKNOWN_STEP}] ${failure.message ?: failure::class.simpleName ?: UNKNOWN_REASON}"

    companion object {
        private const val UNKNOWN_STEP = "unknown"
        private const val UNKNOWN_REASON = "알 수 없는 오류"

        private val ACTIVE_GEMINI_CALL_COUNT = AtomicInteger(0)
        private val log = LoggerFactory.getLogger(PipelineRun::class.java)

        private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000
    }
}
