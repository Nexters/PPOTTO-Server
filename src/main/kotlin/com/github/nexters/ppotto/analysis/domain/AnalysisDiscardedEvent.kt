package com.github.nexters.ppotto.analysis.domain

import com.github.nexters.ppotto.global.identifier.AnalysisId

data class AnalysisDiscardedEvent(
    val analysisId: AnalysisId,
)
