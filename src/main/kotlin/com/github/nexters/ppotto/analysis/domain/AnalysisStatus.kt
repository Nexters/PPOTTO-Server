package com.github.nexters.ppotto.analysis.domain

enum class AnalysisStatus {
    UPLOADING,
    ANALYZING,
    COMPLETED,
    FAILED,
    ;

    companion object {
        val ACTIVE = setOf(UPLOADING, ANALYZING)
    }
}
