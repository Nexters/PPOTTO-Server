package com.github.nexters.ppotto.board.support

import com.github.nexters.ppotto.board.application.port.BoardStickerItem
import java.util.UUID

fun uuidV7(): UUID {
    val value = UUID.randomUUID().toString()
    return UUID.fromString(value.replaceRange(14, 15, "7"))
}

fun boardStickerItem(id: UUID = UUID.randomUUID()): BoardStickerItem =
    BoardStickerItem(
        id = id,
        title = "스티커",
        isNew = true,
        type = "TEXT",
        imageUrl = null,
        textContent = "내용",
        posX = 1.0,
        posY = 2.0,
        scale = 1.0,
        rotation = 0.0,
        zIndex = 1,
        badgeOffsetX = 0.0,
        badgeOffsetY = 0.0,
        badgeRotation = 0.0,
    )
