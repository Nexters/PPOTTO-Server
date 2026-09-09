package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.global.identifier.AnalysisId
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

internal fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000

class PipelineStepTimer {
    fun <T> measuredStep(
        analysisId: AnalysisId,
        step: String,
        onStepFailed: (String) -> Unit = {},
        block: () -> T,
    ): T {
        val startedAt = System.nanoTime()
        log.info("analysis pipeline step started: analysisId={}, step={}", analysisId, step)
        return runCatching(block)
            .onSuccess {
                log.info("analysis pipeline step completed: analysisId={}, step={}, elapsedMs={}", analysisId, step, elapsedMs(startedAt))
            }.onFailure {
                log.error("analysis pipeline step failed: analysisId={}, step={}, elapsedMs={}", analysisId, step, elapsedMs(startedAt), it)
                onStepFailed(step)
            }.getOrThrow()
    }

    fun <T> measuredGeminiCall(
        analysisId: AnalysisId,
        operation: String,
        themeIndex: Int,
        theme: String,
        activeCount: AtomicInteger,
        block: () -> T,
    ): T {
        val startedAt = System.nanoTime()
        val currentActiveCount = activeCount.incrementAndGet()
        log.info(
            "analysis pipeline gemini call started: " +
                "analysisId={}, operation={}, themeIndex={}, theme={}, activeCount={}",
            analysisId,
            operation,
            themeIndex,
            theme,
            currentActiveCount,
        )
        return try {
            block()
        } finally {
            val remainingActiveCount = activeCount.decrementAndGet()
            log.info(
                "analysis pipeline gemini call finished: " +
                    "analysisId={}, operation={}, themeIndex={}, theme={}, activeCount={}, elapsedMs={}",
                analysisId,
                operation,
                themeIndex,
                theme,
                remainingActiveCount,
                elapsedMs(startedAt),
            )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(PipelineStepTimer::class.java)
    }
}
