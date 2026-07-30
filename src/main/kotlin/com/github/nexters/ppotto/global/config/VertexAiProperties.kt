package com.github.nexters.ppotto.global.config

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "vertexai")
data class VertexAiProperties(
    @field:NotBlank
    val project: String,
    @field:NotBlank
    val location: String,
)
