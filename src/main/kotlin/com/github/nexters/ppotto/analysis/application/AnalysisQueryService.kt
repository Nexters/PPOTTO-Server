package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.AnalysisErrorCode
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.PhotoRepository
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.global.identifier.UserId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AnalysisQueryService(
    private val analysisRepository: AnalysisRepository,
    private val photoRepository: PhotoRepository,
) {
    @Transactional(readOnly = true)
    fun hasActiveAnalysis(
        boardId: BoardId,
        userId: UserId,
    ): Boolean = analysisRepository.existsActiveByBoardIdAndUserId(boardId.value, userId.value)

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

    @Transactional(readOnly = true)
    fun ownsAnalysisPhotos(
        userId: UserId,
        boardId: BoardId,
        analysisId: AnalysisId,
        photoIds: Set<PhotoId>,
    ): Boolean =
        analysisRepository
            .findById(analysisId.value)
            ?.takeIf { it.userId == userId.value && it.boardId == boardId.value }
            ?.let { photoRepository.countOwnedByAnalysis(analysisId.value, boardId.value, photoIds.map(PhotoId::value)) == photoIds.size }
            ?: false
}
