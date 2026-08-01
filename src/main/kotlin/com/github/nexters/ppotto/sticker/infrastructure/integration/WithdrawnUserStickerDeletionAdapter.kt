package com.github.nexters.ppotto.sticker.infrastructure.integration

import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.sticker.application.StickerWithdrawalService
import com.github.nexters.ppotto.user.application.port.WithdrawnUserStickerDeletionPort
import org.springframework.stereotype.Component

@Component
class WithdrawnUserStickerDeletionAdapter(
    private val stickerWithdrawalService: StickerWithdrawalService,
) : WithdrawnUserStickerDeletionPort {
    override fun deleteAllByBoardIds(boardIds: Collection<BoardId>): Unit =
        stickerWithdrawalService.deleteAllByBoardIds(boardIds.map(BoardId::value))
}
