package com.github.nexters.ppotto.analysis.presentation.dto

import com.github.nexters.ppotto.analysis.application.AnalysisCreationResult
import java.util.UUID

data class CreateAnalysisResponse(
    val analysisId: UUID,
    val uploads: List<PhotoUploadUrlItem>,
) {
    companion object {
        fun from(result: AnalysisCreationResult): CreateAnalysisResponse =
            CreateAnalysisResponse(
                analysisId = result.analysisId,
                uploads = result.uploads.map { PhotoUploadUrlItem(photoId = it.photoId, uploadUrl = it.uploadUrl) },
            )
    }
}

data class PhotoUploadUrlItem(
    val photoId: UUID,
    val uploadUrl: String,
)
