package com.github.nexters.ppotto.board.domain

import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.DrawingId
import com.github.nexters.ppotto.global.identifier.StickerId
import java.time.Instant

data class Drawing(
    val id: DrawingId,
    val boardId: BoardId,
    val stickerId: StickerId?,
    val scope: DrawingScope,
    val stroke: Map<String, Any?>,
    val color: String,
    val strokeWidth: Double,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class NewDrawing(
    val id: DrawingId,
    val boardId: BoardId,
    val stickerId: StickerId?,
    val scope: DrawingScope,
    val stroke: Map<String, Any?>,
    val color: String,
    val strokeWidth: Double,
) {
    init {
        require((scope == DrawingScope.STICKER) == (stickerId != null))
    }
}
