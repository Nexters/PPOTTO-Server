package com.github.nexters.ppotto.board.domain

import com.github.nexters.ppotto.global.error.CommonErrorCode
import com.github.nexters.ppotto.global.error.InvalidInputException
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
            validateIdentity(id, scope, stickerId, color)
            val invalid = stroke.isEmpty() || !strokeWidth.isPositiveFinite()
            if (invalid) throw InvalidInputException(CommonErrorCode.INVALID_INPUT)
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
            validateIdentity(id, scope, stickerId, color)
            val invalid =
                content.isBlank() ||
                    content.length > Drawing.Text.MAX_CONTENT_LENGTH ||
                    !fontSize.isPositiveFinite() ||
                    !posX.isFinite() ||
                    !posY.isFinite() ||
                    !maxWidth.isPositiveFinite() ||
                    !rotation.isFinite()
            if (invalid) throw InvalidInputException(CommonErrorCode.INVALID_INPUT)
        }
    }
}

private const val UUID_VERSION_7 = 7

private fun validateIdentity(
    id: DrawingId,
    scope: DrawingScope,
    stickerId: StickerId?,
    color: String,
) {
    val invalid =
        id.value.version() != UUID_VERSION_7 ||
            (scope == DrawingScope.STICKER) != (stickerId != null) ||
            color.isBlank()
    if (invalid) throw InvalidInputException(CommonErrorCode.INVALID_INPUT)
}

private fun Double.isPositiveFinite(): Boolean = isFinite() && this > 0
