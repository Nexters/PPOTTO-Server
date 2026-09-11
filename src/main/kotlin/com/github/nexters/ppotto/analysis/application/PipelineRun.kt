package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.AnalysisErrorCode
import com.github.nexters.ppotto.global.error.BusinessException
import com.github.nexters.ppotto.global.identifier.AnalysisId
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

class PipelineRun(
    val analysisId: AnalysisId,
) {
    var failedStep: String? = null
        private set

    var failedCode: AnalysisErrorCode = AnalysisErrorCode.INTERNAL_ERROR
        private set

    fun <T> measured(
        step: String,
        failureCode: AnalysisErrorCode? = null,
        block: () -> T,
    ): T {
        val startedAt = System.nanoTime()
        log.info("analysis pipeline step started: analysisId={}, step={}", analysisId, step)
        return runCatching(block)
            .onSuccess {
                log.info("analysis pipeline step completed: analysisId={}, step={}, elapsedMs={}", analysisId, step, elapsedMs(startedAt))
            }.onFailure {
                log.error("analysis pipeline step failed: analysisId={}, step={}, elapsedMs={}", analysisId, step, elapsedMs(startedAt), it)
                if (failedStep == null) {
                    failedStep = step
                    failedCode =
                        failureCode
                            ?: (it as? BusinessException)?.errorCode as? AnalysisErrorCode
                            ?: AnalysisErrorCode.INTERNAL_ERROR
                }
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
            log.error(
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

    fun failureReason(): String = "[${failedStep ?: UNKNOWN_STEP}] ${failedCode.message}"

    companion object {
        private const val UNKNOWN_STEP = "unknown"

        private val ACTIVE_GEMINI_CALL_COUNT = AtomicInteger(0)
        private val log = LoggerFactory.getLogger(PipelineRun::class.java)

        private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000
    }
}
