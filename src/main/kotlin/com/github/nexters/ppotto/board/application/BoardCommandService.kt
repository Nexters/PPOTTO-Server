package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.port.BoardAnalysisActivityPort
import com.github.nexters.ppotto.board.application.port.BoardStickerCommandPort
import com.github.nexters.ppotto.board.domain.Board
import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.infrastructure.DrawingRepository
import com.github.nexters.ppotto.global.error.ConflictException
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BoardCommandService(
    private val boardRepository: BoardRepository,
    private val boardAccessService: BoardAccessService,
    private val drawingRepository: DrawingRepository,
    private val analysisActivityPort: BoardAnalysisActivityPort,
    private val stickerCommandPort: BoardStickerCommandPort,
) {
    @Transactional
    fun createDefault(userId: UserId): Board = create(userId, null)

    @Transactional
    fun create(
        userId: UserId,
        name: String?,
    ): Board {
        boardRepository.lockCommandsByUserId(userId)
        val count = boardRepository.countByUserId(userId)
        if (count >= Board.MAX_COUNT) {
            throw InvalidInputException(BoardErrorCode.COUNT_LIMIT_EXCEEDED)
        }
        return boardRepository.save(userId, name ?: Board.defaultName(count + 1))
    }

    fun rename(
        boardId: BoardId,
        userId: UserId,
        name: String,
    ): Board =
        boardRepository.updateName(boardId, userId, name)
            ?: throw NotFoundException(BoardErrorCode.NOT_FOUND)

    @Transactional
    fun delete(
        boardId: BoardId,
        userId: UserId,
    ) {
        boardRepository.lockCommandsByUserId(userId)
        boardAccessService.getOwnedByIdForUpdate(boardId, userId)
        validateDeletable(boardId, userId)

        drawingRepository.softDeleteAllByBoardId(boardId)
        stickerCommandPort.deleteAllByBoardId(boardId)
        check(boardRepository.softDelete(boardId, userId)) { "행 잠금을 잡은 보드의 소프트 삭제가 반영되지 않았습니다." }
    }

    private fun validateDeletable(
        boardId: BoardId,
        userId: UserId,
    ) {
        if (boardRepository.countByUserId(userId) <= 1) {
            throw ConflictException(BoardErrorCode.LAST_BOARD_CANNOT_BE_DELETED)
        }
        if (analysisActivityPort.hasActiveAnalysis(boardId, userId)) {
            throw ConflictException(BoardErrorCode.ACTIVE_ANALYSIS_EXISTS)
        }
    }
}
