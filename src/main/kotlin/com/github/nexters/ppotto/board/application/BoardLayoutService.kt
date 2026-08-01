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
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.DrawingId
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.global.identifier.UserId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BoardLayoutService(
    private val boardAccessService: BoardAccessService,
    private val boardRepository: BoardRepository,
    private val drawingRepository: DrawingRepository,
    private val stickerCommandPort: BoardStickerCommandPort,
) {
    @Transactional
    fun update(
        boardId: BoardId,
        userId: UserId,
        command: BoardLayoutUpdateCommand,
    ): Unit =
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
            }.let {
                check(drawingRepository.softDeleteByIds(boardId, it.deletedDrawingIds) == it.deletedDrawingIds.size)
            }

    private fun validateCommand(command: BoardLayoutUpdateCommand): Unit =
        command
            .also(::validateDrawingIds)
            .also { validateStickerLayouts(it.stickers) }
            .let { validateDrawings(it.createdDrawings) }

    private fun validateDrawingIds(command: BoardLayoutUpdateCommand) {
        command.createdDrawings
            .map { it.id }
            .let { createdIds ->
                createdIds
                    .toSet()
                    .takeIf { it.size == createdIds.size }
                    ?.let { uniqueCreatedIds ->
                        command.deletedDrawingIds
                            .toSet()
                            .takeIf { it.size == command.deletedDrawingIds.size }
                            ?.takeIf { deletedIds -> uniqueCreatedIds.none(deletedIds::contains) }
                    }
            } ?: throw InvalidInputException(CommonErrorCode.INVALID_INPUT)
    }

    private fun validateStickerLayouts(stickers: List<BoardStickerLayoutCommand>) {
        stickers
            .map { it.id }
            .takeIf { it.size == it.toSet().size && stickers.none { sticker -> sticker.isInvalid() } }
            ?: throw InvalidInputException(CommonErrorCode.INVALID_INPUT)
    }

    private fun validateDrawings(drawings: List<DrawingCreateCommand>) {
        drawings
            .takeUnless { it.any { drawing -> drawing.isInvalid() } }
            ?: throw InvalidInputException(CommonErrorCode.INVALID_INPUT)
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
            id.value.version() != UUID_VERSION_7,
            (scope == DrawingScope.STICKER) != (stickerId != null),
            stroke.isEmpty(),
            color.isBlank(),
            !strokeWidth.isFinite(),
            strokeWidth <= 0,
        ).any { it }

    private fun validateDrawingOwnership(
        boardId: BoardId,
        command: BoardLayoutUpdateCommand,
    ) {
        command.createdDrawings
            .map { it.id }
            .toSet()
            .let(drawingRepository::findBoardIdsByIds)
            .values
            .any { it != boardId }
            .takeUnless { it }
            ?.let {
                command.deletedDrawingIds
                    .toSet()
                    .takeIf { drawingRepository.findActiveIds(boardId, it) == it }
            } ?: throw InvalidInputException(BoardErrorCode.INVALID_LAYOUT)
    }

    companion object {
        const val MAX_STICKER_TITLE_LENGTH = 15
        const val UUID_VERSION_7 = 7
    }
}

data class BoardLayoutUpdateCommand(
    val stickers: List<BoardStickerLayoutCommand>,
    val createdDrawings: List<DrawingCreateCommand>,
    val deletedDrawingIds: List<DrawingId>,
)

data class DrawingCreateCommand(
    val id: DrawingId,
    val scope: DrawingScope,
    val stickerId: StickerId?,
    val stroke: Map<String, Any?>,
    val color: String,
    val strokeWidth: Double,
) {
    fun toDomain(boardId: BoardId): NewDrawing =
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
