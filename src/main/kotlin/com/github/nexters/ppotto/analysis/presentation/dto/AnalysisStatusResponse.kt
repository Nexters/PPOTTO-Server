package com.github.nexters.ppotto.analysis.presentation.dto

import com.github.nexters.ppotto.analysis.application.AnalysisStatusResult
import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

@Schema(description = "분석 상태")
data class AnalysisStatusResponse(
    val id: UUID,
    val boardId: UUID,
    val status: AnalysisStatus,
    val progress: Int,
    val failedReason: String?,
    val startedAt: Instant?,
    val completedAt: Instant?,
) {
    companion object {
        fun from(result: AnalysisStatusResult): AnalysisStatusResponse =
            AnalysisStatusResponse(
                id = result.id,
                boardId = result.boardId,
                status = result.status,
                progress = result.progress,
                failedReason = result.failedReason,
                startedAt = result.startedAt,
                completedAt = result.completedAt,
            )
    }
}
