package com.github.nexters.ppotto.board.domain

import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.DrawingId
import com.github.nexters.ppotto.global.identifier.StickerId
import java.time.Instant

sealed interface Drawing {
    val id: DrawingId
    val boardId: BoardId
    val stickerId: StickerId?
    val scope: DrawingScope
    val type: DrawingType
    val color: String
    val zIndex: Int
    val createdAt: Instant
    val updatedAt: Instant

    data class Stroke(
        override val id: DrawingId,
        override val boardId: BoardId,
        override val stickerId: StickerId?,
        override val scope: DrawingScope,
        override val color: String,
        override val zIndex: Int,
        override val createdAt: Instant,
        override val updatedAt: Instant,
        val stroke: Map<String, Any?>,
        val strokeWidth: Double,
    ) : Drawing {
        override val type = DrawingType.STROKE
    }

    data class Text(
        override val id: DrawingId,
        override val boardId: BoardId,
        override val stickerId: StickerId?,
        override val scope: DrawingScope,
        override val color: String,
        override val zIndex: Int,
        override val createdAt: Instant,
        override val updatedAt: Instant,
        val content: String,
        val fontSize: Double,
        val posX: Double,
        val posY: Double,
        val maxWidth: Double,
        val rotation: Double,
    ) : Drawing {
        override val type = DrawingType.TEXT

        companion object {
            const val MAX_CONTENT_LENGTH = 32
        }
    }
}

sealed interface NewDrawing {
    val id: DrawingId
    val boardId: BoardId
    val stickerId: StickerId?
    val scope: DrawingScope
    val type: DrawingType
    val color: String
    val zIndex: Int

    data class Stroke(
        override val id: DrawingId,
        override val boardId: BoardId,
        override val stickerId: StickerId?,
        override val scope: DrawingScope,
        override val color: String,
        override val zIndex: Int,
        val stroke: Map<String, Any?>,
        val strokeWidth: Double,
    ) : NewDrawing {
        override val type = DrawingType.STROKE

        init {
            requireScopeMatchesSticker(scope, stickerId)
        }
    }

    data class Text(
        override val id: DrawingId,
        override val boardId: BoardId,
        override val stickerId: StickerId?,
        override val scope: DrawingScope,
        override val color: String,
        override val zIndex: Int,
        val content: String,
        val fontSize: Double,
        val posX: Double,
        val posY: Double,
        val maxWidth: Double,
        val rotation: Double,
    ) : NewDrawing {
        override val type = DrawingType.TEXT

        init {
            requireScopeMatchesSticker(scope, stickerId)
        }
    }
}

private fun requireScopeMatchesSticker(
    scope: DrawingScope,
    stickerId: StickerId?,
) = require((scope == DrawingScope.STICKER) == (stickerId != null))
