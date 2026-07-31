package com.github.nexters.ppotto.analysis.presentation.dto

import com.github.nexters.ppotto.analysis.application.AnalysisCreationResult
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

@Schema(description = "생성된 분석과 사진별 업로드 URL")
data class CreateAnalysisResponse(
    @field:Schema(description = "생성된 분석 ID (uuidv7)", example = "01983f2f-1a2b-7c3d-8e4f-5a6b7c8d9e0f")
    val analysisId: UUID,
    @field:Schema(description = "요청 photos와 같은 순서의 업로드 URL 목록")
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
    @field:Schema(description = "사진 ID (uuidv7)", example = "01983f2e-1a2b-7c3d-8e4f-5a6b7c8d9e0f")
    val photoId: UUID,
    @field:Schema(
        description = "GCS 업로드용 signed URL (만료 15분, 장당 15MB 제한)",
        example = "https://storage.googleapis.com/ppotto-photos/01983f2e.jpg?X-Goog-Expires=900",
    )
    val uploadUrl: String,
)
