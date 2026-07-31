package com.github.nexters.ppotto.global.config

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "vertexai")
data class VertexAiProperties(
    @field:NotBlank
    val project: String,
    @field:NotBlank
    val location: String,
    @field:Positive
    val classifyTimeoutMs: Long,
    @field:Positive
    val stickerGenerationTimeoutMs: Long,
)
