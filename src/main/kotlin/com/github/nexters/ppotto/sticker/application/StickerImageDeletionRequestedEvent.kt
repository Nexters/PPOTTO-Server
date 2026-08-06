package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.global.identifier.StickerId

data class StickerImageDeletionRequestedEvent(
    val stickerId: StickerId,
    val imageKeys: List<String>,
    val reason: StickerImageDeletionReason,
)

enum class StickerImageDeletionReason {
    REGENERATED_IMAGE_REPLACED,
    REGENERATED_IMAGE_DB_UPDATE_FAILED,
}
