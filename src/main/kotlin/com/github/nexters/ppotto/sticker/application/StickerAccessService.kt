package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.board.application.BoardAccessService
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.sticker.domain.Sticker
import com.github.nexters.ppotto.sticker.domain.StickerErrorCode
import com.github.nexters.ppotto.sticker.infrastructure.StickerRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class StickerAccessService(
    private val stickerRepository: StickerRepository,
    private val boardAccessService: BoardAccessService,
) {
    fun getOwned(
        userId: UUID,
        stickerId: UUID,
    ): Sticker =
        (stickerRepository.findById(stickerId) ?: throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND))
            .also { sticker ->
                boardAccessService
                    .getById(sticker.boardId)
                    .takeIf { it.userId == userId }
                    ?: throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND)
            }
}
