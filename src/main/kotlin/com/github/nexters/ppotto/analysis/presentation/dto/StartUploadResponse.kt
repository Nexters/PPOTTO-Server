package com.github.nexters.ppotto.analysis.presentation.dto

import com.github.nexters.ppotto.analysis.application.UploadVerificationResult
import java.util.UUID

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
