package com.github.nexters.ppotto.user.infrastructure

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "user.provider-refresh-token-encryption")
data class ProviderRefreshTokenEncryptionProperties(
    @field:NotBlank
    val keyBase64: String,
)
