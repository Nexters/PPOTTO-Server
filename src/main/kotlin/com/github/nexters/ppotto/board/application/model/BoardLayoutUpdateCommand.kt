package com.github.nexters.ppotto.board.application.model

import com.github.nexters.ppotto.board.application.port.BoardStickerLayoutCommand
import com.github.nexters.ppotto.board.domain.NewDrawing
import com.github.nexters.ppotto.global.error.CommonErrorCode
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.identifier.DrawingId

data class BoardLayoutUpdateCommand(
    val stickers: List<BoardStickerLayoutCommand>,
    val createdDrawings: List<NewDrawing>,
    val deletedDrawingIds: List<DrawingId>,
    val insertedDrawings: List<NewDrawing> = emptyList(),
    val updatedDrawings: List<DrawingLayoutUpdateCommand> = emptyList(),
    val versionedDeletedDrawings: List<DrawingLayoutDeleteCommand> = emptyList(),
) {
    init {
        val createdIds = (createdDrawings + insertedDrawings).map { it.id }
        val updatedIds = updatedDrawings.map { it.drawing.id }
        val deletedIds = deletedDrawingIds + versionedDeletedDrawings.map { it.id }
        val stickerIds = stickers.map { it.id }.toSet()
        val duplicated =
            createdIds.size != createdIds.toSet().size ||
                updatedIds.size != updatedIds.toSet().size ||
                deletedIds.size != deletedIds.toSet().size ||
                stickerIds.size != stickers.size ||
                (createdIds + updatedIds + deletedIds).size != (createdIds + updatedIds + deletedIds).toSet().size
        if (duplicated) throw InvalidInputException(CommonErrorCode.INVALID_INPUT)
    }
}
