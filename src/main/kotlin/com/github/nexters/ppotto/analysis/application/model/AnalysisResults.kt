package com.github.nexters.ppotto.analysis.application.model

import com.github.nexters.ppotto.analysis.domain.Analysis
import com.github.nexters.ppotto.analysis.domain.AnalysisErrorCode
import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import com.github.nexters.ppotto.analysis.domain.Photo
import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.analysis.infrastructure.persistence.PhotoCreate
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.PhotoId
import java.time.Instant
import java.util.UUID

data class AnalysisCreationResult(
    val analysisId: AnalysisId,
    val uploads: List<PhotoUploadUrlItem>,
)

data class PhotoUploadUrlItem(
    val photoId: PhotoId,
    val uploadUrl: String,
)

data class PhotoUploadItemRequest(
    val takenAt: Instant,
    val contentType: PhotoContentType,
    val isRepresentative: Boolean = true,
)

data class PhotoUploadGroupRequest(
    val items: List<PhotoUploadItemRequest>,
) {
    init {
        if (items.size > Photo.MAX_BURST_GROUP_SIZE) throw InvalidInputException(AnalysisErrorCode.BURST_GROUP_SIZE_EXCEEDED)
        if (items.size != 1 && items.count { it.isRepresentative } != 1) {
            throw InvalidInputException(AnalysisErrorCode.INVALID_BURST_GROUP)
        }
    }

    fun toPhotoCreates(): List<PhotoCreate> {
        if (items.size == 1) {
            return items.map { PhotoCreate(it.contentType, it.takenAt, burstGroupId = null, isRepresentative = true) }
        }

        val burstGroupId = UUID.randomUUID()
        return items.map { PhotoCreate(it.contentType, it.takenAt, burstGroupId, it.isRepresentative) }
    }
}

data class CreateAnalysisCommand(
    val photoGroups: List<PhotoUploadGroupRequest>,
) {
    init {
        if (photoGroups.size !in Analysis.MIN_PHOTO_GROUP_COUNT..Analysis.MAX_PHOTO_GROUP_COUNT) {
            throw InvalidInputException(AnalysisErrorCode.GROUP_COUNT_OUT_OF_RANGE)
        }
    }

    fun toPhotoCreates(): List<PhotoCreate> = photoGroups.flatMap { it.toPhotoCreates() }
}

data class UploadVerificationResult(
    val uploadedCount: Int,
    val failedCount: Int,
    val failedPhotoIds: List<PhotoId>,
)

data class AnalysisStatusResult(
    val id: AnalysisId,
    val boardId: BoardId,
    val status: AnalysisStatus,
    val progress: Int,
    val failedCode: AnalysisErrorCode?,
    val failedReason: String?,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val notificationRequested: Boolean,
) {
    companion object {
        fun from(analysis: Analysis): AnalysisStatusResult =
            AnalysisStatusResult(
                id = analysis.id,
                boardId = analysis.boardId,
                status = analysis.status,
                progress = analysis.progress,
                failedCode = analysis.failedCode,
                failedReason = analysis.failedReason,
                startedAt = analysis.startedAt,
                completedAt = analysis.completedAt,
                notificationRequested = analysis.notificationRequestedAt != null,
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
