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
        command
            .also {
                boardRepository.lockCommandsByUserId(userId)
                boardAccessService.getOwnedById(boardId, userId)
                validateCommand(it)
                validateDrawingOwnership(boardId, it)
            }.also {
                (
                    it.stickers
                        .map { sticker -> sticker.id }
                        .toSet() +
                        it.createdDrawings.mapNotNull { drawing -> drawing.stickerId }
                ).let { stickerIds -> stickerCommandPort.validateOwnedByBoard(boardId, stickerIds) }
            }.also {
                stickerCommandPort.updateLayouts(boardId, it.stickers)
            }.also {
                drawingRepository.upsertAll(it.createdDrawings.map { drawing -> drawing.toDomain(boardId) })
            }.also {
                check(drawingRepository.softDeleteByIds(boardId, it.deletedDrawingIds) == it.deletedDrawingIds.size)
            }
    }

    private fun validateCommand(command: BoardLayoutUpdateCommand) {
        validateDrawingIds(command)
        validateStickerLayouts(command.stickers)
        validateDrawings(command.createdDrawings)
    }

    private fun validateDrawingIds(command: BoardLayoutUpdateCommand) {
        command.createdDrawings.map { it.id }.let { createdIds ->
            command.deletedDrawingIds.let { deletedIds ->
                if (
                    createdIds.size != createdIds.toSet().size ||
                    deletedIds.size != deletedIds.toSet().size ||
                    createdIds.any(deletedIds.toSet()::contains)
                ) {
                    throw InvalidInputException(CommonErrorCode.INVALID_INPUT)
                }
            }
        }
    }

    private fun validateStickerLayouts(stickers: List<BoardStickerLayoutCommand>) {
        stickers.map { it.id }.let { stickerIds ->
            if (stickerIds.size != stickerIds.toSet().size || stickers.any { it.isInvalid() }) {
                throw InvalidInputException(CommonErrorCode.INVALID_INPUT)
            }
        }
    }

    private fun validateDrawings(drawings: List<DrawingCreateCommand>) {
        if (drawings.any { it.isInvalid() }) {
            throw InvalidInputException(CommonErrorCode.INVALID_INPUT)
        }
    }

    private fun BoardStickerLayoutCommand.isInvalid(): Boolean =
        listOf(
            title != null && (title.isBlank() || title.length > MAX_STICKER_TITLE_LENGTH),
            !posX.isFinite(),
            !posY.isFinite(),
            !scale.isFinite(),
            scale <= 0,
            !rotation.isFinite(),
            !badgeOffsetX.isFinite(),
            !badgeOffsetY.isFinite(),
            !badgeRotation.isFinite(),
        ).any { it }

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
        command.createdDrawings
            .map { it.id }
            .toSet()
            .let(drawingRepository::findBoardIdsByIds)
            .values
            .any { it != boardId }
            .takeIf { it }
            ?.let { throw InvalidInputException(BoardErrorCode.INVALID_LAYOUT) }

        command.deletedDrawingIds
            .toSet()
            .takeIf { drawingRepository.findActiveIds(boardId, it) != it }
            ?.let { throw InvalidInputException(BoardErrorCode.INVALID_LAYOUT) }
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
