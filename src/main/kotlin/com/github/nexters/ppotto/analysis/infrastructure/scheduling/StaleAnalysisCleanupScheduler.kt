package com.github.nexters.ppotto.analysis.infrastructure.scheduling

import com.github.nexters.ppotto.analysis.application.AnalysisNotificationService
import com.github.nexters.ppotto.analysis.application.StaleAnalysisCleanupService
import com.github.nexters.ppotto.analysis.infrastructure.config.StaleAnalysisCleanupProperties
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.logging.bestEffort
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import java.time.Instant
import java.time.temporal.ChronoUnit

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "analysis.stale-cleanup", name = ["enabled"], havingValue = "true")
class StaleAnalysisCleanupScheduler(
    private val cleanupService: StaleAnalysisCleanupService,
    private val analysisNotificationService: AnalysisNotificationService,
    private val properties: StaleAnalysisCleanupProperties,
) {
    @Scheduled(cron = "\${analysis.stale-cleanup.cron}", zone = TIME_ZONE)
    fun cleanup() {
        val updatedBefore = Instant.now().minus(properties.timeoutMinutes, ChronoUnit.MINUTES)
        val expired = cleanupService.cleanup(updatedBefore, properties.batchSize)
        if (expired.isNotEmpty()) {
            log.warn("멈춘 분석 만료 처리 count={} analysisIds={}", expired.size, expired)
        }
        expired.forEach(::notifyFailureBestEffort)
    }

    private fun notifyFailureBestEffort(analysisId: AnalysisId) {
        bestEffort(log, "만료 분석 실패 푸시 알림 발행(analysisId=$analysisId)") {
            analysisNotificationService.notifyFailureIfRequested(analysisId)
        }
    }

    companion object {
        private const val TIME_ZONE = "Asia/Seoul"

        private val log = LoggerFactory.getLogger(StaleAnalysisCleanupScheduler::class.java)
    }
}
