package com.github.nexters.ppotto.analysis.infrastructure.integration

import com.github.nexters.ppotto.analysis.application.AnalysisQueryService
import com.github.nexters.ppotto.board.application.port.BoardAnalysisActivityPort
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class BoardAnalysisActivityAdapter(
    private val analysisQueryService: AnalysisQueryService,
) : BoardAnalysisActivityPort {
    override fun hasActiveAnalysis(
        boardId: UUID,
        userId: UUID,
    ): Boolean = analysisQueryService.hasActiveAnalysis(boardId, userId)
}
