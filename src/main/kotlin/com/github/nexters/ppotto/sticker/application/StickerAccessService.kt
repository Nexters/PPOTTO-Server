package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.board.application.BoardAccessService
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.sticker.domain.Sticker
import com.github.nexters.ppotto.sticker.domain.StickerErrorCode
import com.github.nexters.ppotto.sticker.infrastructure.StickerRepository
import org.springframework.stereotype.Service

@Service
class StickerAccessService(
    private val stickerRepository: StickerRepository,
    private val boardAccessService: BoardAccessService,
) {
    fun getOwned(
        userId: UserId,
        stickerId: StickerId,
    ): Sticker =
        (stickerRepository.findById(stickerId) ?: throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND))
            .also { sticker ->
                boardAccessService
                    .getById(sticker.boardId)
                    .takeIf { it.userId == userId }
                    ?: throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND)
            }

    fun getWithOwnership(
        userId: UserId?,
        stickerId: StickerId,
    ): StickerWithOwnership =
        (stickerRepository.findById(stickerId) ?: throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND))
            .let { sticker ->
                StickerWithOwnership(
                    sticker = sticker,
                    isOwner = userId != null && boardAccessService.getById(sticker.boardId).userId == userId,
                )
            }
}

data class StickerWithOwnership(
    val sticker: Sticker,
    val isOwner: Boolean,
)
