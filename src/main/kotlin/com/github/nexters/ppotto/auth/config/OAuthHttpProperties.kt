package com.github.nexters.ppotto.auth.config

import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties("auth.oauth")
data class OAuthHttpProperties(
    @field:Positive
    val connectTimeoutMillis: Long,
    @field:Positive
    val readTimeoutMillis: Long,
)
