package com.github.nexters.ppotto.board.presentation.dto

import com.github.nexters.ppotto.board.application.BoardDetail
import com.github.nexters.ppotto.board.application.BoardSummary
import com.github.nexters.ppotto.board.application.port.BoardStickerItem
import com.github.nexters.ppotto.board.domain.Board
import com.github.nexters.ppotto.board.domain.Drawing
import com.github.nexters.ppotto.board.domain.DrawingScope
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

@Schema(description = "보드 요약")
data class BoardResponse(
    val id: UUID,
    val name: String,
) {
    companion object {
        fun from(board: Board): BoardResponse = BoardResponse(board.id, board.name)

        fun from(board: BoardSummary): BoardResponse = BoardResponse(board.id, board.name)
    }
}

@Schema(description = "보드와 배치된 스티커 및 그림")
data class BoardDetailResponse(
    val id: UUID,
    val name: String,
    val stickers: List<StickerResponse>,
    val drawings: List<DrawingResponse>,
) {
    companion object {
        fun from(board: BoardDetail): BoardDetailResponse =
            BoardDetailResponse(
                id = board.id,
                name = board.name,
                stickers = board.stickers.map(StickerResponse::from),
                drawings = board.drawings.map(DrawingResponse::from),
            )
    }
}

@Schema(description = "보드에 배치된 스티커")
data class StickerResponse(
    val id: UUID,
    val title: String,
    val isNew: Boolean,
    val type: String,
    val imageUrl: String?,
    val textContent: String?,
    val posX: Double,
    val posY: Double,
    val scale: Double,
    val rotation: Double,
    val zIndex: Int,
    val badgeOffsetX: Double,
    val badgeOffsetY: Double,
    val badgeRotation: Double,
) {
    companion object {
        fun from(sticker: BoardStickerItem): StickerResponse =
            StickerResponse(
                id = sticker.id,
                title = sticker.title,
                isNew = sticker.isNew,
                type = sticker.type,
                imageUrl = sticker.imageUrl,
                textContent = sticker.textContent,
                posX = sticker.posX,
                posY = sticker.posY,
                scale = sticker.scale,
                rotation = sticker.rotation,
                zIndex = sticker.zIndex,
                badgeOffsetX = sticker.badgeOffsetX,
                badgeOffsetY = sticker.badgeOffsetY,
                badgeRotation = sticker.badgeRotation,
            )
    }
}

@Schema(description = "보드 또는 스티커 위의 그림")
data class DrawingResponse(
    val id: UUID,
    val scope: DrawingScope,
    val stickerId: UUID?,
    val stroke: Map<String, Any?>,
    val color: String,
    val strokeWidth: Double,
) {
    companion object {
        fun from(drawing: Drawing): DrawingResponse =
            DrawingResponse(
                id = drawing.id,
                scope = drawing.scope,
                stickerId = drawing.stickerId,
                stroke = drawing.stroke,
                color = drawing.color,
                strokeWidth = drawing.strokeWidth,
            )
    }
}
