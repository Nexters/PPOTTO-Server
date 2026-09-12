package com.github.nexters.ppotto.analysis.domain

import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.PhotoId
import java.time.Instant
import java.util.UUID

data class Photo(
    val id: PhotoId,
    val analysisId: AnalysisId,
    val boardId: BoardId,
    val contentType: PhotoContentType,
    val uploadStatus: UploadStatus,
    val uploadedAt: Instant?,
    val takenAt: Instant?,
    val burstGroupId: UUID?,
    val isRepresentative: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        const val MAX_BURST_GROUP_SIZE = 10
    }
}
