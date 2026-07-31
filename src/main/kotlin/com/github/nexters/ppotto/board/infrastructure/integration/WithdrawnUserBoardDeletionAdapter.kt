package com.github.nexters.ppotto.board.infrastructure.integration

import com.github.nexters.ppotto.board.application.BoardWithdrawalService
import com.github.nexters.ppotto.user.application.port.WithdrawnUserBoardDeletionPort
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class WithdrawnUserBoardDeletionAdapter(
    private val boardWithdrawalService: BoardWithdrawalService,
) : WithdrawnUserBoardDeletionPort {
    override fun findAllBoardIds(userId: UUID): List<UUID> = boardWithdrawalService.findAllBoardIds(userId)

    override fun deleteAllByUserId(userId: UUID): Unit = boardWithdrawalService.deleteAllByUserId(userId)
}
