package com.github.nexters.ppotto.board.support

import com.github.nexters.ppotto.board.application.port.BoardStickerItem
import com.github.nexters.ppotto.board.domain.BoardStickerType
import com.github.nexters.ppotto.board.domain.DrawingScope
import com.github.nexters.ppotto.board.domain.NewDrawing
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.DrawingId
import com.github.nexters.ppotto.global.identifier.StickerId
import java.util.UUID

fun uuidV7(): UUID {
    val value = UUID.randomUUID().toString()
    return UUID.fromString(value.replaceRange(14, 15, "7"))
}

fun newDrawing(
    boardId: BoardId,
    stickerId: StickerId? = null,
    id: DrawingId = DrawingId(uuidV7()),
    stroke: Map<String, Any?> = mapOf("points" to listOf(listOf(1.0, 2.0))),
    color: String = "#FFFFFF",
    strokeWidth: Double = 2.0,
    zIndex: Int = 0,
): NewDrawing.Stroke =
    NewDrawing.Stroke(
        id = id,
        boardId = boardId,
        stickerId = stickerId,
        scope = stickerId?.let { DrawingScope.STICKER } ?: DrawingScope.BOARD,
        color = color,
        zIndex = zIndex,
        stroke = stroke,
        strokeWidth = strokeWidth,
    )

fun newTextDrawing(
    boardId: BoardId,
    stickerId: StickerId? = null,
    id: DrawingId = DrawingId(uuidV7()),
    content: String = "여름 휴가",
    color: String = "#FFFFFF",
    zIndex: Int = 0,
    fontSize: Double = 26.0,
    posX: Double = 80.0,
    posY: Double = 290.5,
    maxWidth: Double = 280.0,
    rotation: Double = 0.0,
): NewDrawing.Text =
    NewDrawing.Text(
        id = id,
        boardId = boardId,
        stickerId = stickerId,
        scope = stickerId?.let { DrawingScope.STICKER } ?: DrawingScope.BOARD,
        color = color,
        zIndex = zIndex,
        content = content,
        fontSize = fontSize,
        posX = posX,
        posY = posY,
        maxWidth = maxWidth,
        rotation = rotation,
    )

fun boardStickerItem(id: StickerId = StickerId(UUID.randomUUID())): BoardStickerItem =
    BoardStickerItem(
        id = id,
        title = "스티커",
        isNew = true,
        type = BoardStickerType.TEXT,
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
