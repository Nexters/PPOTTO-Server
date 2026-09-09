package com.github.nexters.ppotto.analysis.domain

import com.github.nexters.ppotto.global.error.ConflictException
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import java.time.Instant

data class Analysis(
    val id: AnalysisId,
    val userId: UserId,
    val boardId: BoardId,
    val status: AnalysisStatus,
    val progress: Int,
    val failedReason: String?,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    fun requireUploading() {
        if (status != AnalysisStatus.UPLOADING) throw ConflictException(AnalysisErrorCode.ALREADY_STARTED_OR_FINISHED)
    }
}
