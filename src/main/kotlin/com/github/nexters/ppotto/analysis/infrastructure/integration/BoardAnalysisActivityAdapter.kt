package com.github.nexters.ppotto.analysis.infrastructure.integration

import com.github.nexters.ppotto.analysis.application.AnalysisQueryService
import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import com.github.nexters.ppotto.board.application.port.BoardActiveAnalysis
import com.github.nexters.ppotto.board.application.port.BoardActiveAnalysisPort
import com.github.nexters.ppotto.board.application.port.BoardAnalysisActivityPort
import com.github.nexters.ppotto.board.application.port.BoardAnalysisStatus
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import org.springframework.stereotype.Component

@Component
class BoardAnalysisActivityAdapter(
    private val analysisQueryService: AnalysisQueryService,
) : BoardAnalysisActivityPort,
    BoardActiveAnalysisPort {
    override fun hasActiveAnalysis(
        boardId: BoardId,
        userId: UserId,
    ): Boolean = analysisQueryService.hasActiveAnalysis(boardId, userId)

    override fun findByUserId(userId: UserId): BoardActiveAnalysis? =
        analysisQueryService
            .getActiveAnalysis(userId)
            ?.let { BoardActiveAnalysis(it.boardId, it.status.toBoardStatus()) }

    private fun AnalysisStatus.toBoardStatus() =
        when (this) {
            AnalysisStatus.UPLOADING -> BoardAnalysisStatus.UPLOADING
            AnalysisStatus.ANALYZING -> BoardAnalysisStatus.ANALYZING
            AnalysisStatus.COMPLETED, AnalysisStatus.FAILED -> error("진행 중이 아닌 분석은 보드에 노출하지 않습니다: $this")
        }
}
