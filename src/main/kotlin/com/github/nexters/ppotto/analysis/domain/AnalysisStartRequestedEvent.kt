package com.github.nexters.ppotto.analysis.domain

import java.util.UUID

data class AnalysisStartRequestedEvent(
    val analysisId: UUID,
    val photos: List<PhotoRef>,
)
