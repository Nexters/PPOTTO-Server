package com.github.nexters.ppotto.analysis.domain

import com.github.nexters.ppotto.global.identifier.AnalysisId

data class AnalysisCanceledEvent(
    val analysisId: AnalysisId,
)
