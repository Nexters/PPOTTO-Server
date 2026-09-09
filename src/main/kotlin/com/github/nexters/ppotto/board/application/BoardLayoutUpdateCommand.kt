package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.port.BoardStickerLayoutCommand
import com.github.nexters.ppotto.board.domain.NewDrawing
import com.github.nexters.ppotto.global.error.CommonErrorCode
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.identifier.DrawingId

data class BoardLayoutUpdateCommand(
    val stickers: List<BoardStickerLayoutCommand>,
    val createdDrawings: List<NewDrawing>,
    val deletedDrawingIds: List<DrawingId>,
) {
    init {
        val createdIds = createdDrawings.map { it.id }.toSet()
        val deletedIds = deletedDrawingIds.toSet()
        val stickerIds = stickers.map { it.id }.toSet()
        val duplicated =
            createdIds.size != createdDrawings.size ||
                deletedIds.size != deletedDrawingIds.size ||
                stickerIds.size != stickers.size ||
                createdIds.any(deletedIds::contains)
        if (duplicated) throw InvalidInputException(CommonErrorCode.INVALID_INPUT)
    }
}
