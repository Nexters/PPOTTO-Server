package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.application.MAX_STALE_CLEANUP_BATCH_SIZE
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "analysis.stale-cleanup")
data class StaleAnalysisCleanupProperties(
    val enabled: Boolean,

    @field:Positive
    val timeoutMinutes: Long,

    @field:Positive
    @field:Max(MAX_STALE_CLEANUP_BATCH_SIZE.toLong())
    val batchSize: Int,

    @field:NotBlank
    val cron: String,
)
