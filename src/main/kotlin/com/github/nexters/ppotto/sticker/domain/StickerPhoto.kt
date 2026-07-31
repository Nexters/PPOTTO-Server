package com.github.nexters.ppotto.sticker.domain

import java.util.UUID

data class StickerPhoto(
    val id: UUID,
    val stickerId: UUID,
    val photoId: UUID,
)
