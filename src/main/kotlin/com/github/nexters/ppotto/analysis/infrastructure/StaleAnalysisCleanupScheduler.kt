package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.application.StaleAnalysisCleanupService
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
    private val properties: StaleAnalysisCleanupProperties,
) {
    @Scheduled(cron = "\${analysis.stale-cleanup.cron}", zone = TIME_ZONE)
    fun cleanup() {
        val updatedBefore = Instant.now().minus(properties.timeoutMinutes, ChronoUnit.MINUTES)
        val expired = cleanupService.cleanup(updatedBefore, properties.batchSize)
        if (expired.isNotEmpty()) {
            log.warn("멈춘 분석 만료 처리 count={} analysisIds={}", expired.size, expired)
        }
    }

    companion object {
        private const val TIME_ZONE = "Asia/Seoul"

        private val log = LoggerFactory.getLogger(StaleAnalysisCleanupScheduler::class.java)
    }
}
