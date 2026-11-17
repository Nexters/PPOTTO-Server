package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.model.BoardEvent
import com.github.nexters.ppotto.board.application.model.BoardLayoutConflictException
import com.github.nexters.ppotto.board.application.model.BoardLayoutUpdateCommand
import com.github.nexters.ppotto.board.application.model.DrawingLayoutUpdateCommand
import com.github.nexters.ppotto.board.application.port.BoardStickerCommandPort
import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.infrastructure.DrawingRepository
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.transaction.afterCommit
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BoardLayoutService(
    private val boardAccessService: BoardAccessService,
    private val drawingRepository: DrawingRepository,
    private val stickerCommandPort: BoardStickerCommandPort,
    private val boardEventBroker: BoardEventBroker,
) {
    @Transactional
    fun update(
        boardId: BoardId,
        userId: UserId,
        command: BoardLayoutUpdateCommand,
    ) {
        boardAccessService.getAccessibleByIdForUpdate(boardId, userId)
        validateDrawingOwnership(boardId, command)

        if (!stickerCommandPort.ownsAll(boardId, referencedStickerIds(command))) {
            throw InvalidInputException(BoardErrorCode.INVALID_LAYOUT)
        }

        stickerCommandPort.updateLayouts(boardId, command.stickers)
        drawingRepository.upsertAll(command.createdDrawings)
        drawingRepository.insertAll(command.insertedDrawings)
        command.updatedDrawings.forEach(::updateDrawing)

        val softDeleted = drawingRepository.softDeleteByIds(boardId, command.deletedDrawingIds)
        check(softDeleted == command.deletedDrawingIds.size) { "소유권을 확인한 그림의 소프트 삭제가 반영되지 않았습니다." }
        command.versionedDeletedDrawings.forEach { deletion ->
            if (!drawingRepository.softDeleteVersioned(boardId, deletion)) {
                throw BoardLayoutConflictException(drawingIds = setOf(deletion.id))
            }
        }

        afterCommit { boardEventBroker.publish(BoardEvent.updated(boardId)) }
    }

    private fun updateDrawing(update: DrawingLayoutUpdateCommand) {
        if (drawingRepository.updateVersioned(update)) return
        if (!drawingRepository.matchesIdentity(update.drawing)) {
            val active = drawingRepository.findActiveIds(update.drawing.boardId, listOf(update.drawing.id))
            if (update.drawing.id in active) throw InvalidInputException(BoardErrorCode.INVALID_LAYOUT)
        }
        throw BoardLayoutConflictException(drawingIds = setOf(update.drawing.id))
    }

    private fun referencedStickerIds(command: BoardLayoutUpdateCommand): Set<StickerId> =
        command.stickers
            .map { it.id }
            .toSet() +
            (command.createdDrawings + command.insertedDrawings + command.updatedDrawings.map { it.drawing })
                .mapNotNull { it.stickerId }

    private fun validateDrawingOwnership(
        boardId: BoardId,
        command: BoardLayoutUpdateCommand,
    ) {
        val createdIds =
            (command.createdDrawings + command.insertedDrawings)
                .map { it.id }
                .toSet()
        val foreignCreated =
            drawingRepository
                .findBoardIdsByIds(createdIds)
                .values
                .any { it != boardId }
        val updatedIds =
            command.updatedDrawings
                .map { it.drawing.id }
                .toSet()
        val versionedDeletedIds =
            command.versionedDeletedDrawings
                .map { it.id }
                .toSet()
        val changedIds = updatedIds + versionedDeletedIds
        val changedBoardIds = drawingRepository.findBoardIdsByIds(changedIds)
        val deletedIds = command.deletedDrawingIds.toSet()
        val invalidChanged = changedBoardIds.keys != changedIds || changedBoardIds.values.any { it != boardId }
        val invalidDeleted = drawingRepository.findActiveIds(boardId, deletedIds) != deletedIds
        if (foreignCreated || invalidChanged || invalidDeleted) {
            throw InvalidInputException(BoardErrorCode.INVALID_LAYOUT)
        }
    }
}
