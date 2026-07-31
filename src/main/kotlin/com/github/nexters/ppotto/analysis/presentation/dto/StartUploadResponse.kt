package com.github.nexters.ppotto.analysis.presentation.dto

import com.github.nexters.ppotto.analysis.application.UploadVerificationResult
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

@Schema(description = "사진 업로드 확인 결과")
data class StartUploadResponse(
    val uploadedCount: Int,
    val failedCount: Int,
    val failedPhotoIds: List<UUID>,
) {
    companion object {
        fun from(result: UploadVerificationResult): StartUploadResponse =
            StartUploadResponse(result.uploadedCount, result.failedCount, result.failedPhotoIds)
    }
}
