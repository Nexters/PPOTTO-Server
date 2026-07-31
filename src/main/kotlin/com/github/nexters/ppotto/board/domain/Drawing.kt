package com.github.nexters.ppotto.board.domain

import java.time.Instant
import java.util.UUID

data class Drawing(
    val id: UUID,
    val boardId: UUID,
    val stickerId: UUID?,
    val scope: DrawingScope,
    val stroke: Map<String, Any?>,
    val color: String,
    val strokeWidth: Double,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class NewDrawing(
    val id: UUID,
    val boardId: UUID,
    val stickerId: UUID?,
    val scope: DrawingScope,
    val stroke: Map<String, Any?>,
    val color: String,
    val strokeWidth: Double,
) {
    init {
        ((scope == DrawingScope.STICKER) == (stickerId != null))
            .let { require(it) }
    }
}
