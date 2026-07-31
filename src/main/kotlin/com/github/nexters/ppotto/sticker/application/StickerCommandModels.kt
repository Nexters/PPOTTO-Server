package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.sticker.domain.StickerLayout
import java.util.UUID

data class StickerTitleResult(
    val id: UUID,
    val title: String,
)

data class StickerLayoutCommand(
    val id: UUID,
    val title: String? = null,
    val posX: Double,
    val posY: Double,
    val scale: Double,
    val rotation: Double,
    val zIndex: Int,
    val badgeOffsetX: Double,
    val badgeOffsetY: Double,
    val badgeRotation: Double,
) {
    fun toDomain() =
        StickerLayout(
            title = title,
            posX = posX,
            posY = posY,
            scale = scale,
            rotation = rotation,
            zIndex = zIndex,
            badgeOffsetX = badgeOffsetX,
            badgeOffsetY = badgeOffsetY,
            badgeRotation = badgeRotation,
        )
}
