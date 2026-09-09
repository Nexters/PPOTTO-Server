package com.github.nexters.ppotto.analysis.domain

import com.github.nexters.ppotto.global.identifier.AnalysisId

data class AnalysisStartRequestedEvent(
    val analysisId: AnalysisId,
    val photos: List<PhotoRef>,
)
