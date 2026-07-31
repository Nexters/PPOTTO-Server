package com.github.nexters.ppotto.board.support

import com.github.nexters.ppotto.board.application.port.BoardStickerItem
import com.github.nexters.ppotto.board.domain.DrawingScope
import com.github.nexters.ppotto.board.domain.NewDrawing
import java.util.UUID

fun uuidV7(): UUID {
    val value = UUID.randomUUID().toString()
    return UUID.fromString(value.replaceRange(14, 15, "7"))
}

fun newDrawing(
    boardId: UUID,
    stickerId: UUID? = null,
    id: UUID = uuidV7(),
    stroke: Map<String, Any?> = mapOf("points" to listOf(listOf(1.0, 2.0))),
    color: String = "#FFFFFF",
    strokeWidth: Double = 2.0,
): NewDrawing =
    NewDrawing(
        id = id,
        boardId = boardId,
        stickerId = stickerId,
        scope = stickerId?.let { DrawingScope.STICKER } ?: DrawingScope.BOARD,
        stroke = stroke,
        color = color,
        strokeWidth = strokeWidth,
    )

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
