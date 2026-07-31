package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.port.BoardStickerCommandPort
import com.github.nexters.ppotto.board.application.port.BoardStickerLayoutCommand
import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.domain.DrawingScope
import com.github.nexters.ppotto.board.domain.NewDrawing
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.infrastructure.DrawingRepository
import com.github.nexters.ppotto.global.error.CommonErrorCode
import com.github.nexters.ppotto.global.error.InvalidInputException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class BoardLayoutService(
    private val boardAccessService: BoardAccessService,
    private val boardRepository: BoardRepository,
    private val drawingRepository: DrawingRepository,
    private val stickerCommandPort: BoardStickerCommandPort,
) {
    @Transactional
    fun update(
        boardId: UUID,
        userId: UUID,
        command: BoardLayoutUpdateCommand,
    ) {
        boardRepository.lockCommandsByUserId(userId)
        boardAccessService.getOwnedById(boardId, userId)
        validateCommand(command)
        validateDrawingOwnership(boardId, command)

        val stickerIds =
            command.stickers
                .map { it.id }
                .toSet() +
                command.createdDrawings.mapNotNull { it.stickerId }
        stickerCommandPort.validateOwnedByBoard(boardId, stickerIds)

        stickerCommandPort.updateLayouts(boardId, command.stickers)
        drawingRepository.upsertAll(command.createdDrawings.map { it.toDomain(boardId) })

        val deletedCount = drawingRepository.softDeleteByIds(boardId, command.deletedDrawingIds)
        check(deletedCount == command.deletedDrawingIds.size)
    }

    private fun validateCommand(command: BoardLayoutUpdateCommand) {
        validateDrawingIds(command)
        validateStickerLayouts(command.stickers)
        validateDrawings(command.createdDrawings)
    }

    private fun validateDrawingIds(command: BoardLayoutUpdateCommand) {
        val createdIds = command.createdDrawings.map { it.id }
        val deletedIds = command.deletedDrawingIds
        if (
            createdIds.size != createdIds.toSet().size ||
            deletedIds.size != deletedIds.toSet().size ||
            createdIds.any(deletedIds.toSet()::contains)
        ) {
            throw InvalidInputException(CommonErrorCode.INVALID_INPUT)
        }
    }

    private fun validateStickerLayouts(stickers: List<BoardStickerLayoutCommand>) {
        val stickerIds = stickers.map { it.id }
        if (stickerIds.size != stickerIds.toSet().size || stickers.any { it.isInvalid() }) {
            throw InvalidInputException(CommonErrorCode.INVALID_INPUT)
        }
    }

    private fun validateDrawings(drawings: List<DrawingCreateCommand>) {
        if (drawings.any { it.isInvalid() }) {
            throw InvalidInputException(CommonErrorCode.INVALID_INPUT)
        }
    }

    private fun BoardStickerLayoutCommand.isInvalid(): Boolean {
        val invalidTitle = title != null && (title.isBlank() || title.length > MAX_STICKER_TITLE_LENGTH)
        return listOf(
            invalidTitle,
            !posX.isFinite(),
            !posY.isFinite(),
            !scale.isFinite(),
            scale <= 0,
            !rotation.isFinite(),
            !badgeOffsetX.isFinite(),
            !badgeOffsetY.isFinite(),
            !badgeRotation.isFinite(),
        ).any { it }
    }

    private fun DrawingCreateCommand.isInvalid(): Boolean =
        listOf(
            id.version() != UUID_VERSION_7,
            (scope == DrawingScope.STICKER) != (stickerId != null),
            stroke.isEmpty(),
            color.isBlank(),
            !strokeWidth.isFinite(),
            strokeWidth <= 0,
        ).any { it }

    private fun validateDrawingOwnership(
        boardId: UUID,
        command: BoardLayoutUpdateCommand,
    ) {
        val createdIds =
            command.createdDrawings
                .map { it.id }
                .toSet()
        val existingBoardIds = drawingRepository.findBoardIdsByIds(createdIds)
        if (existingBoardIds.values.any { it != boardId }) {
            throw InvalidInputException(BoardErrorCode.INVALID_LAYOUT)
        }

        val deletedIds = command.deletedDrawingIds.toSet()
        if (drawingRepository.findActiveIds(boardId, deletedIds) != deletedIds) {
            throw InvalidInputException(BoardErrorCode.INVALID_LAYOUT)
        }
    }

    companion object {
        const val MAX_STICKER_TITLE_LENGTH = 15
        const val UUID_VERSION_7 = 7
    }
}

data class BoardLayoutUpdateCommand(
    val stickers: List<BoardStickerLayoutCommand>,
    val createdDrawings: List<DrawingCreateCommand>,
    val deletedDrawingIds: List<UUID>,
)

data class DrawingCreateCommand(
    val id: UUID,
    val scope: DrawingScope,
    val stickerId: UUID?,
    val stroke: Map<String, Any?>,
    val color: String,
    val strokeWidth: Double,
) {
    fun toDomain(boardId: UUID): NewDrawing =
        NewDrawing(
            id = id,
            boardId = boardId,
            stickerId = stickerId,
            scope = scope,
            stroke = stroke,
            color = color,
            strokeWidth = strokeWidth,
        )
}
