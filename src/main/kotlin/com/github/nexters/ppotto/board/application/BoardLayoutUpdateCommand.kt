package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.port.BoardStickerLayoutCommand
import com.github.nexters.ppotto.board.domain.NewDrawing
import com.github.nexters.ppotto.global.identifier.DrawingId

data class BoardLayoutUpdateCommand(
    val stickers: List<BoardStickerLayoutCommand>,
    val createdDrawings: List<NewDrawing>,
    val deletedDrawingIds: List<DrawingId>,
)
