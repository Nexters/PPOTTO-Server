package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.sticker.domain.RecapCommentPosition
import com.github.nexters.ppotto.sticker.infrastructure.StickerRecapRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RecapCommentCommandService(
    private val stickerAccessService: StickerAccessService,
    private val stickerRecapRepository: StickerRecapRepository,
) {
    @Transactional
    fun updatePositions(
        userId: UserId,
        stickerId: StickerId,
        positions: List<RecapCommentPosition>,
    ): Unit =
        positions
            .also { validatePositions(it) }
            .also { stickerAccessService.getOwned(userId, stickerId) }
            .let { stickerRecapRepository.updatePositions(stickerId, it) }
            .takeIf { updatedCount -> updatedCount == positions.size }
            ?.let { } ?: throw uneditableComment()

    private fun validatePositions(positions: List<RecapCommentPosition>) {
        positions
            .map { it.id }
            .takeIf { it.size == it.toSet().size }
            ?.takeIf { positions.none { position -> !position.posX.isFinite() || !position.posY.isFinite() } }
            ?: throw uneditableComment()
    }

    private fun uneditableComment() = InvalidInputException(message = "수정할 수 없는 코멘트가 포함되어 있습니다.")
}
