package com.github.nexters.ppotto.sticker.infrastructure

import com.github.nexters.ppotto.board.application.port.BoardStickerCommandPort
import com.github.nexters.ppotto.board.application.port.BoardStickerItem
import com.github.nexters.ppotto.board.application.port.BoardStickerLayoutCommand
import com.github.nexters.ppotto.board.application.port.BoardStickerQueryPort
import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.sticker.application.StickerCommandService
import com.github.nexters.ppotto.sticker.application.StickerItemResult
import com.github.nexters.ppotto.sticker.application.StickerLayoutCommand
import com.github.nexters.ppotto.sticker.application.StickerQueryService
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class BoardStickerAdapter(
    private val stickerQueryService: StickerQueryService,
    private val stickerCommandService: StickerCommandService,
) : BoardStickerQueryPort,
    BoardStickerCommandPort {
    override fun getByBoardId(boardId: UUID): List<BoardStickerItem> =
        stickerQueryService
            .getByBoardId(boardId)
            .map { it.toBoardItem() }

    override fun validateOwnedByBoard(
        boardId: UUID,
        stickerIds: Set<UUID>,
    ) {
        if (!stickerCommandService.validateOwnedByBoard(boardId, stickerIds)) {
            throw InvalidInputException(BoardErrorCode.INVALID_LAYOUT)
        }
    }

    override fun updateLayouts(
        boardId: UUID,
        layouts: List<BoardStickerLayoutCommand>,
    ) {
        stickerCommandService.updateLayouts(boardId, layouts.map { it.toStickerCommand() })
    }

    override fun deleteAllByBoardId(boardId: UUID) {
        stickerCommandService.deleteAllByBoardId(boardId)
    }

    private fun StickerItemResult.toBoardItem() =
        BoardStickerItem(
            id = id,
            title = title,
            isNew = isNew,
            type = type.name,
            imageUrl = imageUrl,
            textContent = textContent,
            posX = posX,
            posY = posY,
            scale = scale,
            rotation = rotation,
            zIndex = zIndex,
            badgeOffsetX = badgeOffsetX,
            badgeOffsetY = badgeOffsetY,
            badgeRotation = badgeRotation,
        )

    private fun BoardStickerLayoutCommand.toStickerCommand() =
        StickerLayoutCommand(
            id = id,
            title = title,
            posX = posX,
            posY = posY,
            scale = scale,
            rotation = rotation,
            zIndex = zIndex,
            badgeOffsetX = badgeOffsetX,
            badgeOffsetY = badgeOffsetY,
            badgeRotation = badgeRotation,
        )
}
