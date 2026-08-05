package com.github.nexters.ppotto.analysis.domain

import java.util.UUID

data class AnalysisCanceledEvent(
    val analysisId: UUID,
)
