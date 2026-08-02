package com.github.nexters.ppotto.analysis.presentation.dto

import com.github.nexters.ppotto.analysis.application.UploadVerificationResult
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

@Schema(description = "사진 업로드 확인 결과")
data class StartUploadResponse(
    @field:Schema(description = "업로드가 확인되어 분석에 사용할 사진 수", example = "97")
    val uploadedCount: Int,

    @field:Schema(description = "GCS에 없어 분석에서 제외된 사진 수", example = "1")
    val failedCount: Int,

    @field:Schema(
        description = "제외된 사진 ID 목록",
        example = "[\"01983f2e-9f8e-7d6c-b5a4-3c2b1a0f9e8d\"]",
    )
    val failedPhotoIds: List<UUID>,
) {
    companion object {
        fun from(result: UploadVerificationResult): StartUploadResponse =
            StartUploadResponse(result.uploadedCount, result.failedCount, result.failedPhotoIds)
    }
}
