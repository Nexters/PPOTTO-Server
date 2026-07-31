package com.github.nexters.ppotto.global.config

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "gcs")
data class GcsProperties(
    @field:NotBlank
    val bucket: String,

    @field:NotBlank
    val credentialsPath: String,

    @field:Positive
    @field:Max(SIGNED_URL_MAX_EXPIRATION_MINUTES)
    val uploadSignedUrlExpirationMinutes: Long,

    @field:Positive
    @field:Max(SIGNED_URL_MAX_EXPIRATION_MINUTES)
    val readSignedUrlExpirationMinutes: Long,

    @field:Positive
    val timeoutMillis: Long,
)

private const val SIGNED_URL_MAX_EXPIRATION_MINUTES = 24L * 60
