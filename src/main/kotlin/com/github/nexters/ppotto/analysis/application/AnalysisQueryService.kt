package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.AnalysisErrorCode
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.global.error.NotFoundException
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

    @Transactional(readOnly = true)
    fun getActiveAnalysis(userId: UUID): AnalysisStatusResult? =
        analysisRepository
            .findActiveByUserId(userId)
            ?.let(AnalysisStatusResult::from)

    @Transactional(readOnly = true)
    fun getAnalysis(
        analysisId: UUID,
        userId: UUID,
    ): AnalysisStatusResult =
        analysisRepository
            .findByIdAndUserId(analysisId, userId)
            ?.let(AnalysisStatusResult::from)
            ?: throw NotFoundException(AnalysisErrorCode.ANALYSIS_NOT_FOUND)
}
