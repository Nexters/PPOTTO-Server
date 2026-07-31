package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.infrastructure.BoardWithdrawalRepository
import com.github.nexters.ppotto.board.infrastructure.DrawingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class BoardWithdrawalService(
    private val boardWithdrawalRepository: BoardWithdrawalRepository,
    private val drawingRepository: DrawingRepository,
) {
    @Transactional(readOnly = true)
    fun findAllBoardIds(userId: UUID): List<UUID> = boardWithdrawalRepository.findAllIdsByUserId(userId)

    @Transactional
    fun deleteAllByUserId(userId: UUID) {
        boardWithdrawalRepository
            .findAllIdsByUserId(userId)
            .let(drawingRepository::hardDeleteAllByBoardIds)
            .let { boardWithdrawalRepository.hardDeleteAllByUserId(userId) }
    }
}
