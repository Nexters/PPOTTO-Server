package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.sticker.domain.RecapCommentPosition
import com.github.nexters.ppotto.sticker.domain.StickerErrorCode
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
    ) {
        validatePositions(positions)
        stickerAccessService.getOwned(userId, stickerId)

        val updatedCount = stickerRecapRepository.updatePositions(stickerId, positions)
        if (updatedCount != positions.size) {
            throw InvalidInputException(StickerErrorCode.UNEDITABLE_RECAP_COMMENT)
        }
    }

    private fun validatePositions(positions: List<RecapCommentPosition>) {
        val ids = positions.map { it.id }
        if (ids.size != ids.toSet().size) {
            throw InvalidInputException(StickerErrorCode.UNEDITABLE_RECAP_COMMENT)
        }
        if (positions.any { !it.posX.isFinite() || !it.posY.isFinite() }) {
            throw InvalidInputException(StickerErrorCode.UNEDITABLE_RECAP_COMMENT)
        }
    }
}
