package com.github.nexters.ppotto.analysis.infrastructure.config

import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "analysis.pipeline")
data class AnalysisPipelineProperties(
    @field:Positive
    val maxConcurrentRuns: Int,

    @field:Positive
    val resumeLimit: Int,
)
