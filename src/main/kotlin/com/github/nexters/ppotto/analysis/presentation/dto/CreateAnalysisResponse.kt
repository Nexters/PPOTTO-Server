package com.github.nexters.ppotto.analysis.presentation.dto

import com.github.nexters.ppotto.analysis.application.AnalysisCreationResult
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

@Schema(description = "생성된 분석과 사진별 업로드 URL")
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

@Schema(description = "사진 ID와 GCS 업로드 URL")
data class PhotoUploadUrlItem(
    val photoId: UUID,
    val uploadUrl: String,
)
