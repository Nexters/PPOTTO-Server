package com.github.nexters.ppotto.auth.config

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties("auth.jwt")
data class JwtAuthProperties(
    @field:NotBlank
    val issuer: String,
    @field:Size(min = 32)
    val secret: String,
    @field:Positive
    val accessTokenExpirationSeconds: Long,
    @field:Positive
    val refreshTokenExpirationDays: Long,
)
