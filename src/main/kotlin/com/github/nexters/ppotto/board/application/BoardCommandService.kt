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
        boardRepository
            .lockCommandsByUserId(userId)
            .let { boardRepository.countByUserId(userId) }
            .also {
                if (it >= Board.MAX_COUNT) {
                    throw InvalidInputException(BoardErrorCode.COUNT_LIMIT_EXCEEDED)
                }
            }.let { name ?: Board.defaultName(it + 1) }
            .also(::validateName)
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
    ) {
        boardRepository.lockCommandsByUserId(userId)
        getOwnedByIdForUpdate(boardId, userId)
        boardId
            .also { validateDeletable(it, userId) }
            .also(drawingCommandService::deleteAllByBoardId)
            .also(stickerCommandPort::deleteAllByBoardId)
            .also { check(boardRepository.softDelete(it, userId)) }
    }

    private fun getOwnedByIdForUpdate(
        boardId: UUID,
        userId: UUID,
    ): Board =
        boardRepository.findOwnedByIdForUpdate(boardId, userId)
            ?: throw NotFoundException(BoardErrorCode.NOT_FOUND)

    private fun validateDeletable(
        boardId: UUID,
        userId: UUID,
    ) {
        if (boardRepository.countByUserId(userId) <= 1) {
            throw ConflictException(BoardErrorCode.LAST_BOARD_CANNOT_BE_DELETED)
        }
        if (analysisActivityPort.hasActiveAnalysis(boardId, userId)) {
            throw ConflictException(BoardErrorCode.ACTIVE_ANALYSIS_EXISTS)
        }
    }

    private fun validateName(name: String) {
        if (name.isBlank() || name.length > Board.MAX_NAME_LENGTH) {
            throw InvalidInputException(CommonErrorCode.INVALID_INPUT)
        }
    }
}
