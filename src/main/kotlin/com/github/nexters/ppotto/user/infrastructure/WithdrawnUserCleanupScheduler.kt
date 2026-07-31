package com.github.nexters.ppotto.user.infrastructure

import com.github.nexters.ppotto.user.application.WithdrawnUserCleanupService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import java.time.Instant
import java.time.temporal.ChronoUnit

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "user.withdrawn-cleanup", name = ["enabled"], havingValue = "true")
class WithdrawnUserCleanupScheduleConfig {
    @Bean
    fun withdrawnUserCleanupScheduler(
        cleanupService: WithdrawnUserCleanupService,
        properties: WithdrawnUserCleanupProperties,
    ): WithdrawnUserCleanupScheduler = WithdrawnUserCleanupScheduler(cleanupService, properties)
}

class WithdrawnUserCleanupScheduler(
    private val cleanupService: WithdrawnUserCleanupService,
    private val properties: WithdrawnUserCleanupProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${user.withdrawn-cleanup.cron}", zone = TIME_ZONE)
    fun cleanup() {
        Instant
            .now()
            .minus(properties.retentionDays, ChronoUnit.DAYS)
            .let { cleanupService.cleanup(it, properties.batchSize) }
            .let { log.info("탈퇴 사용자 정리 완료 attempted={} deleted={}", it.attempted, it.deletedUserIds.size) }
    }

    private companion object {
        const val TIME_ZONE = "Asia/Seoul"
    }
}
