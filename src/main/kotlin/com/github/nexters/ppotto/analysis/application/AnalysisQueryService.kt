package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.application.model.AnalysisStatusResult
import com.github.nexters.ppotto.analysis.domain.AnalysisErrorCode
import com.github.nexters.ppotto.analysis.infrastructure.persistence.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.persistence.PhotoRepository
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.global.identifier.UserId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AnalysisQueryService(
    private val analysisRepository: AnalysisRepository,
    private val photoRepository: PhotoRepository,
) {
    fun hasActiveAnalysis(
        boardId: BoardId,
        userId: UserId,
    ): Boolean = analysisRepository.existsActiveByBoardIdAndUserId(boardId, userId)

    fun getActiveAnalysis(userId: UserId): AnalysisStatusResult? =
        analysisRepository
            .findActiveByUserId(userId)
            ?.let(AnalysisStatusResult::from)

    fun getAnalysis(
        analysisId: AnalysisId,
        userId: UserId,
    ): AnalysisStatusResult =
        analysisRepository
            .findByIdAndUserId(analysisId, userId)
            ?.let(AnalysisStatusResult::from)
            ?: throw NotFoundException(AnalysisErrorCode.ANALYSIS_NOT_FOUND)

    @Transactional(readOnly = true)
    fun ownsAnalysisPhotos(
        userId: UserId,
        boardId: BoardId,
        analysisId: AnalysisId,
        photoIds: Set<PhotoId>,
    ): Boolean {
        val analysis = analysisRepository.findById(analysisId) ?: return false
        if (analysis.userId != userId || analysis.boardId != boardId) return false

        return photoRepository.countOwnedByAnalysis(analysisId, boardId, photoIds) == photoIds.size
    }
}
