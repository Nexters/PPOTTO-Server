package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.port.BoardStickerCommandPort
import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.infrastructure.DrawingRepository
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.lock.AdvisoryLock
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BoardLayoutService(
    private val boardAccessService: BoardAccessService,
    private val drawingRepository: DrawingRepository,
    private val stickerCommandPort: BoardStickerCommandPort,
) {
    @Transactional
    @AdvisoryLock(namespace = BOARD_USER_LOCK_NAMESPACE, key = "#userId")
    fun update(
        boardId: BoardId,
        userId: UserId,
        command: BoardLayoutUpdateCommand,
    ) {
        boardAccessService.getOwnedById(boardId, userId)
        validateDrawingOwnership(boardId, command)

        if (!stickerCommandPort.ownsAll(boardId, referencedStickerIds(command))) {
            throw InvalidInputException(BoardErrorCode.INVALID_LAYOUT)
        }

        stickerCommandPort.updateLayouts(boardId, command.stickers)
        drawingRepository.upsertAll(command.createdDrawings)

        val softDeleted = drawingRepository.softDeleteByIds(boardId, command.deletedDrawingIds)
        check(softDeleted == command.deletedDrawingIds.size) { "소유권을 확인한 그림의 소프트 삭제가 반영되지 않았습니다." }
    }

    private fun referencedStickerIds(command: BoardLayoutUpdateCommand): Set<StickerId> =
        command.stickers
            .map { it.id }
            .toSet() + command.createdDrawings.mapNotNull { it.stickerId }

    private fun validateDrawingOwnership(
        boardId: BoardId,
        command: BoardLayoutUpdateCommand,
    ) {
        val createdIds =
            command.createdDrawings
                .map { it.id }
                .toSet()
        val foreignCreated =
            drawingRepository
                .findBoardIdsByIds(createdIds)
                .values
                .any { it != boardId }
        if (foreignCreated) throw InvalidInputException(BoardErrorCode.INVALID_LAYOUT)

        val deletedIds = command.deletedDrawingIds.toSet()
        if (drawingRepository.findActiveIds(boardId, deletedIds) != deletedIds) {
            throw InvalidInputException(BoardErrorCode.INVALID_LAYOUT)
        }
    }
}
