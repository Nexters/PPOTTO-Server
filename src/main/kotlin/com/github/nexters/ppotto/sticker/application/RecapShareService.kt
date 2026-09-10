package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.sticker.domain.StickerErrorCode
import com.github.nexters.ppotto.sticker.infrastructure.StickerCommandRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class RecapShareService(
    private val stickerAccessService: StickerAccessService,
    private val stickerCommandRepository: StickerCommandRepository,
) {
    fun share(
        userId: UserId,
        stickerId: StickerId,
        includePhotos: Boolean,
    ): String {
        val sticker = stickerAccessService.getOwned(userId, stickerId)
        val shareToken = sticker.shareToken ?: UUID.randomUUID().toString()

        if (!stickerCommandRepository.updateShare(sticker.id, shareToken, includePhotos)) {
            throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND)
        }
        return shareToken
    }

    fun unshare(
        userId: UserId,
        stickerId: StickerId,
    ) {
        val sticker = stickerAccessService.getOwned(userId, stickerId)
        if (sticker.shareToken == null) {
            return
        }

        if (!stickerCommandRepository.clearShare(sticker.id)) {
            throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND)
        }
    }
}
