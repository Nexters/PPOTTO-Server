package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.port.BoardAnalysisActivityPort
import com.github.nexters.ppotto.board.application.port.BoardStickerCommandPort
import com.github.nexters.ppotto.board.domain.Board
import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.error.CommonErrorCode
import com.github.nexters.ppotto.global.error.ConflictException
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.error.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class BoardCommandService(
    private val boardRepository: BoardRepository,
    private val boardAccessService: BoardAccessService,
    private val drawingCommandService: BoardDrawingCommandService,
    private val analysisActivityPort: BoardAnalysisActivityPort,
    private val stickerCommandPort: BoardStickerCommandPort,
) {
    @Transactional
    fun createDefault(userId: UUID): Board = create(userId, null)

    @Transactional
    fun create(
        userId: UUID,
        name: String?,
    ): Board =
        (
            boardRepository
                .lockCommandsByUserId(userId)
                .let { boardRepository.countByUserId(userId) }
                .takeIf { it < Board.MAX_COUNT }
                ?.let { name ?: Board.defaultName(it + 1) }
                ?: throw InvalidInputException(BoardErrorCode.COUNT_LIMIT_EXCEEDED)
        ).also(::validateName)
            .let { boardRepository.save(userId, it) }

    @Transactional
    fun rename(
        boardId: UUID,
        userId: UUID,
        name: String,
    ): Board =
        name
            .also(::validateName)
            .let { boardRepository.updateName(boardId, userId, it) }
            ?: throw NotFoundException(BoardErrorCode.NOT_FOUND)

    @Transactional
    fun delete(
        boardId: UUID,
        userId: UUID,
    ): Unit =
        boardRepository
            .lockCommandsByUserId(userId)
            .let { boardAccessService.getOwnedByIdForUpdate(boardId, userId) }
            .let { boardId }
            .also { validateDeletable(it, userId) }
            .also(drawingCommandService::deleteAllByBoardId)
            .also(stickerCommandPort::deleteAllByBoardId)
            .let { check(boardRepository.softDelete(it, userId)) }

    private fun validateDeletable(
        boardId: UUID,
        userId: UUID,
    ) {
        boardRepository
            .countByUserId(userId)
            .takeIf { it > 1 }
            ?: throw ConflictException(BoardErrorCode.LAST_BOARD_CANNOT_BE_DELETED)
        boardId
            .takeUnless { analysisActivityPort.hasActiveAnalysis(it, userId) }
            ?: throw ConflictException(BoardErrorCode.ACTIVE_ANALYSIS_EXISTS)
    }

    private fun validateName(name: String) {
        name
            .takeUnless { it.isBlank() || it.length > Board.MAX_NAME_LENGTH }
            ?: throw InvalidInputException(CommonErrorCode.INVALID_INPUT)
    }
}
