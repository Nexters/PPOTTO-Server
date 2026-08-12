package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.Analysis
import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.global.identifier.PhotoId
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
    val contentType: PhotoContentType,
    val burstGroupId: UUID? = null,
    val isRepresentative: Boolean = true,
)

data class PhotoUploadGroupRequest(
    val items: List<PhotoUploadItemRequest>,
)

data class UploadVerificationResult(
    val uploadedCount: Int,
    val failedCount: Int,
    val failedPhotoIds: List<UUID>,
)

data class AnalysisStatusResult(
    val id: UUID,
    val boardId: UUID,
    val status: AnalysisStatus,
    val progress: Int,
    val failedReason: String?,
    val startedAt: Instant?,
    val completedAt: Instant?,
) {
    companion object {
        fun from(analysis: Analysis): AnalysisStatusResult =
            AnalysisStatusResult(
                id = analysis.id,
                boardId = analysis.boardId,
                status = analysis.status,
                progress = analysis.progress,
                failedReason = analysis.failedReason,
                startedAt = analysis.startedAt,
                completedAt = analysis.completedAt,
            )
    }
}

data class PhotoReadResult(
    val id: PhotoId,
    val imageUrl: String,
    val takenAt: Instant,
    val isRepresentative: Boolean,
    val burstGroupId: UUID?,
)
