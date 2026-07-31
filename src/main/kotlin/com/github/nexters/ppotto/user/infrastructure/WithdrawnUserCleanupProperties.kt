package com.github.nexters.ppotto.user.infrastructure

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
    @field:Max(MAX_BATCH_SIZE)
    val batchSize: Int,

    @field:NotBlank
    val cron: String,
)

private const val MAX_BATCH_SIZE = 1_000L
