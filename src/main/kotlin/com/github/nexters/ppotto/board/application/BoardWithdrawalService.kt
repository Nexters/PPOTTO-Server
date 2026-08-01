package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.infrastructure.BoardWithdrawalRepository
import com.github.nexters.ppotto.board.infrastructure.DrawingRepository
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BoardWithdrawalService(
    private val boardWithdrawalRepository: BoardWithdrawalRepository,
    private val drawingRepository: DrawingRepository,
) {
    @Transactional(readOnly = true)
    fun findAllBoardIds(userId: UserId): List<BoardId> = boardWithdrawalRepository.findAllIdsByUserId(userId)

    @Transactional
    fun deleteAllByUserId(userId: UserId) {
        boardWithdrawalRepository
            .findAllIdsByUserId(userId)
            .let(drawingRepository::hardDeleteAllByBoardIds)
            .let { boardWithdrawalRepository.hardDeleteAllByUserId(userId) }
    }
}
