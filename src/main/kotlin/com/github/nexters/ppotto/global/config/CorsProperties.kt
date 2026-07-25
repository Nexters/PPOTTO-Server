package com.github.nexters.ppotto.global.config

import jakarta.validation.constraints.NotEmpty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "cors")
data class CorsProperties(
    @field:NotEmpty
    val allowedOrigins: List<@NotEmpty String>,
)
