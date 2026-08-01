package com.github.nexters.ppotto.analysis.infrastructure.integration

import com.github.nexters.ppotto.analysis.application.AnalysisQueryService
import com.github.nexters.ppotto.board.application.port.BoardAnalysisActivityPort
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import org.springframework.stereotype.Component

@Component
class BoardAnalysisActivityAdapter(
    private val analysisQueryService: AnalysisQueryService,
) : BoardAnalysisActivityPort {
    override fun hasActiveAnalysis(
        boardId: BoardId,
        userId: UserId,
    ): Boolean = analysisQueryService.hasActiveAnalysis(boardId, userId)
}
