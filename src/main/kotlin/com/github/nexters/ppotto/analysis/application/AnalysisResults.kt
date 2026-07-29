package com.github.nexters.ppotto.analysis.application

import java.time.Instant
import java.util.UUID

data class AnalysisCreationResult(
    val analysisId: UUID,
    val uploads: List<PhotoUploadUrlItem>,
)

data class PhotoUploadUrlItem(
    val photoId: UUID,
    val uploadUrl: String,
)

data class PhotoUploadItemRequest(
    val takenAt: Instant,
    val contentType: String?,
)

data class UploadVerificationResult(
    val uploadedCount: Int,
    val failedCount: Int,
    val failedPhotoIds: List<UUID>,
)
