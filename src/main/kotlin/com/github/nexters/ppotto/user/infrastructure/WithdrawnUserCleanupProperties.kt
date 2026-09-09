package com.github.nexters.ppotto.user.infrastructure

import com.github.nexters.ppotto.user.application.MAX_CLEANUP_BATCH_SIZE
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "user.withdrawn-cleanup")
data class WithdrawnUserCleanupProperties(
    val enabled: Boolean,

    @field:Positive
    val retentionDays: Long,

    @field:Positive
    @field:Max(MAX_CLEANUP_BATCH_SIZE)
    val batchSize: Int,

    @field:NotBlank
    val cron: String,
)
