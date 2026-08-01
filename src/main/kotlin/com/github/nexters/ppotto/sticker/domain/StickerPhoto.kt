package com.github.nexters.ppotto.sticker.domain

import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.global.identifier.StickerId
import java.util.UUID

data class StickerPhoto(
    val id: UUID,
    val stickerId: StickerId,
    val photoId: PhotoId,
)
