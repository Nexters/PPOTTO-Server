package com.github.nexters.ppotto.analysis.presentation.dto

import com.github.nexters.ppotto.analysis.application.model.AnalysisCreationResult
import com.github.nexters.ppotto.analysis.application.model.PhotoUploadUrlItem
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.PhotoId
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "생성된 분석과 사진별 업로드 URL")
data class CreateAnalysisResponse(
    @field:Schema(description = "생성된 분석 ID (uuidv7)", example = "01983f2f-1a2b-7c3d-8e4f-5a6b7c8d9e0f")
    val analysisId: AnalysisId,

    @field:Schema(description = "요청 photos와 같은 순서의 업로드 URL 목록")
    val uploads: List<PhotoUploadUrlResponse>,
) {
    companion object {
        fun from(result: AnalysisCreationResult): CreateAnalysisResponse =
            CreateAnalysisResponse(
                analysisId = result.analysisId,
                uploads = result.uploads.map(PhotoUploadUrlResponse::from),
            )
    }
}

@Schema(description = "사진 ID와 GCS 업로드 URL")
data class PhotoUploadUrlResponse(
    @field:Schema(description = "사진 ID (uuidv7)", example = "01983f2e-1a2b-7c3d-8e4f-5a6b7c8d9e0f")
    val photoId: PhotoId,

    @field:Schema(
        description = "GCS 업로드용 signed URL (만료 15분, 장당 15MB 제한)",
        example = "https://storage.googleapis.com/ppotto-photos/01983f2e.jpg?X-Goog-Expires=900",
    )
    val uploadUrl: String,
) {
    companion object {
        fun from(item: PhotoUploadUrlItem): PhotoUploadUrlResponse = PhotoUploadUrlResponse(item.photoId, item.uploadUrl)
    }
}
