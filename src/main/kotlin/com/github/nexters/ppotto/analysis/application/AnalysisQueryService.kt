package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AnalysisQueryService(
    private val analysisRepository: AnalysisRepository,
) {
    @Transactional(readOnly = true)
    fun hasActiveAnalysis(
        boardId: UUID,
        userId: UUID,
    ): Boolean = analysisRepository.existsActiveByBoardIdAndUserId(boardId, userId)
}
