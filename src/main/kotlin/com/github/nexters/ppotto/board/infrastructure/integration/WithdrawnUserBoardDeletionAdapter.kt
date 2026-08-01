package com.github.nexters.ppotto.board.infrastructure.integration

import com.github.nexters.ppotto.board.application.BoardWithdrawalService
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.user.application.port.WithdrawnUserBoardDeletionPort
import org.springframework.stereotype.Component

@Component
class WithdrawnUserBoardDeletionAdapter(
    private val boardWithdrawalService: BoardWithdrawalService,
) : WithdrawnUserBoardDeletionPort {
    override fun findAllBoardIds(userId: UserId): List<BoardId> =
        boardWithdrawalService
            .findAllBoardIds(userId.value)
            .map(::BoardId)

    override fun deleteAllByUserId(userId: UserId): Unit = boardWithdrawalService.deleteAllByUserId(userId.value)
}
