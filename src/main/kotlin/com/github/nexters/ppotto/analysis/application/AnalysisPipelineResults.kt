package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.AnalysisErrorCode
import com.github.nexters.ppotto.analysis.domain.ThemeComment
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.PhotoId

data class AnalysisPipelineResult(
    val analysisId: AnalysisId,
    val themes: List<ThemeAnalysisResult>,
)

data class ThemeAnalysisResult(
    val theme: String,
    val categorizedPhotoIds: List<PhotoId>,
    val badge: String,
    val text: String,
    val stickerSourcePhotoId: PhotoId,
    val stickerImageKey: String?,
    val stickerMainColor: String,
    val comments: List<ThemeComment>,
    val failedCode: AnalysisErrorCode? = null,
)
