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
    ): Sticker {
        val sticker =
            stickerRepository.findById(stickerId)
                ?: throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND)
        if (boardAccessService.getById(sticker.boardId).userId != userId) {
            throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND)
        }
        return sticker
    }

    fun getWithOwnership(
        userId: UserId?,
        stickerId: StickerId,
    ): StickerWithOwnership {
        val sticker =
            stickerRepository.findById(stickerId)
                ?: throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND)
        return StickerWithOwnership(
            sticker = sticker,
            isOwner = userId != null && boardAccessService.getById(sticker.boardId).userId == userId,
        )
    }
}

data class StickerWithOwnership(
    val sticker: Sticker,
    val isOwner: Boolean,
)
