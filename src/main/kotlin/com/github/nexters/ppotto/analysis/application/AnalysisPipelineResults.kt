package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.ThemeComment
import java.util.UUID

data class AnalysisPipelineResult(
    val analysisId: UUID,
    val themes: List<ThemeAnalysisResult>,
)

data class ThemeAnalysisResult(
    val theme: String,
    val categorizedPhotoIds: List<UUID>,
    val badge: String,
    val text: String,
    val stickerTargetSubject: String,
    val stickerSourcePhotoId: UUID,
    val stickerImageKey: String?,
    val stickerMainColor: String,
    val comments: List<ThemeComment>,
)
