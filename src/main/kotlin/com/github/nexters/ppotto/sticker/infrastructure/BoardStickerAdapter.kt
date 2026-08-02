package com.github.nexters.ppotto.sticker.infrastructure

import com.github.nexters.ppotto.board.application.port.BoardStickerCommandPort
import com.github.nexters.ppotto.board.application.port.BoardStickerItem
import com.github.nexters.ppotto.board.application.port.BoardStickerLayoutCommand
import com.github.nexters.ppotto.board.application.port.BoardStickerQueryPort
import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.domain.BoardStickerType
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.sticker.application.StickerCommandService
import com.github.nexters.ppotto.sticker.application.StickerItemResult
import com.github.nexters.ppotto.sticker.application.StickerLayoutCommand
import com.github.nexters.ppotto.sticker.application.StickerQueryService
import com.github.nexters.ppotto.sticker.domain.StickerType
import org.springframework.stereotype.Component

@Component
class BoardStickerAdapter(
    private val stickerQueryService: StickerQueryService,
    private val stickerCommandService: StickerCommandService,
) : BoardStickerQueryPort,
    BoardStickerCommandPort {
    override fun getByBoardId(boardId: BoardId): List<BoardStickerItem> =
        stickerQueryService
            .getByBoardId(boardId)
            .map { it.toBoardItem() }

    override fun validateOwnedByBoard(
        boardId: BoardId,
        stickerIds: Set<StickerId>,
    ) {
        stickerCommandService
            .validateOwnedByBoard(boardId, stickerIds)
            .takeIf { it }
            ?: throw InvalidInputException(BoardErrorCode.INVALID_LAYOUT)
    }

    override fun updateLayouts(
        boardId: BoardId,
        layouts: List<BoardStickerLayoutCommand>,
    ): Unit =
        layouts
            .map { it.toStickerCommand() }
            .let { stickerCommandService.updateLayouts(boardId, it) }

    override fun deleteAllByBoardId(boardId: BoardId): Unit = stickerCommandService.deleteAllByBoardId(boardId)

    private fun StickerItemResult.toBoardItem() =
        BoardStickerItem(
            id = id,
            title = title,
            isNew = isNew,
            type = type.toBoardType(),
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

    private fun StickerType.toBoardType() =
        when (this) {
            StickerType.IMAGE -> BoardStickerType.IMAGE
            StickerType.TEXT -> BoardStickerType.TEXT
        }

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
