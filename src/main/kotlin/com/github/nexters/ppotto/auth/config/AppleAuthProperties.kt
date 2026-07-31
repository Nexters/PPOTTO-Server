package com.github.nexters.ppotto.auth.config

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.net.URI

@Validated
@ConfigurationProperties("auth.apple")
data class AppleAuthProperties(
    @field:NotBlank
    val clientId: String,
    @field:NotBlank
    val teamId: String,
    @field:NotBlank
    val keyId: String,
    @field:NotBlank
    val privateKeyPath: String,
    @field:NotBlank
    val issuer: String,
    @field:NotNull
    val jwksUri: URI,
    @field:NotNull
    val tokenUri: URI,
    @field:NotNull
    val revokeUri: URI,
    @field:Positive
    @field:Max(180)
    val clientSecretExpirationDays: Long,
    @field:Positive
    val jwksCacheSeconds: Long,
)
