package com.github.nexters.ppotto.notification.infrastructure.config

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "fcm")
data class FcmProperties(
    @field:NotBlank
    val credentialsPath: String,

    @field:Positive
    val timeoutMillis: Long,
)
