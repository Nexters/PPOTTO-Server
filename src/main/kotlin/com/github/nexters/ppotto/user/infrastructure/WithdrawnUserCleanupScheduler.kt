package com.github.nexters.ppotto.user.infrastructure

import com.github.nexters.ppotto.user.application.WithdrawnUserCleanupService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import java.time.Instant
import java.time.temporal.ChronoUnit

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "user.withdrawn-cleanup", name = ["enabled"], havingValue = "true")
class WithdrawnUserCleanupScheduler(
    private val cleanupService: WithdrawnUserCleanupService,
    private val properties: WithdrawnUserCleanupProperties,
) {
    @Scheduled(cron = "\${user.withdrawn-cleanup.cron}", zone = TIME_ZONE)
    fun cleanup() {
        val deletedBefore = Instant.now().minus(properties.retentionDays, ChronoUnit.DAYS)
        val result = cleanupService.cleanup(deletedBefore, properties.batchSize)
        log.info("탈퇴 사용자 정리 완료 attempted={} deleted={}", result.attempted, result.deletedUserIds.size)
    }

    companion object {
        private const val TIME_ZONE = "Asia/Seoul"

        private val log = LoggerFactory.getLogger(WithdrawnUserCleanupScheduler::class.java)
    }
}
