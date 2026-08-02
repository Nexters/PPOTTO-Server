package com.github.nexters.ppotto.analysis.presentation.dto

import com.github.nexters.ppotto.analysis.application.AnalysisStatusResult
import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

@Schema(description = "분석 상태")
data class AnalysisStatusResponse(
    @field:Schema(description = "분석 ID (uuidv7)", example = "01983f2f-1a2b-7c3d-8e4f-5a6b7c8d9e0f")
    val id: UUID,

    @field:Schema(description = "결과 스티커가 붙을 보드 ID", example = "01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b")
    val boardId: UUID,

    @field:Schema(
        description = "UPLOADING 업로드 중 / ANALYZING 분석·생성·배치 중 / COMPLETED 완료 / FAILED 실패·취소·만료",
        example = "ANALYZING",
    )
    val status: AnalysisStatus,

    @field:Schema(description = "진행률 0~100", example = "45")
    val progress: Int,

    @field:Schema(description = "실패 사유", example = "AI 분석 호출이 반복 실패했습니다.")
    val failedReason: String?,

    @field:Schema(description = "분석이 시작된 시각", example = "2026-07-27T14:02:11+09:00")
    val startedAt: Instant?,

    @field:Schema(description = "분석이 완료된 시각", example = "2026-07-27T14:03:38+09:00")
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
