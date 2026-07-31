package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.domain.Board
import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.error.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class BoardAccessService(
    private val boardRepository: BoardRepository,
) {
    @Transactional(readOnly = true)
    fun getById(id: UUID): Board =
        boardRepository.findById(id)
            ?: throw NotFoundException(BoardErrorCode.NOT_FOUND)

    @Transactional(readOnly = true)
    fun getOwnedById(
        boardId: UUID,
        userId: UUID,
    ): Board =
        boardRepository.findOwnedById(boardId, userId)
            ?: throw NotFoundException(BoardErrorCode.NOT_FOUND)

    @Transactional
    fun getOwnedByIdForUpdate(
        boardId: UUID,
        userId: UUID,
    ): Board =
        boardRepository.findOwnedByIdForUpdate(boardId, userId)
            ?: throw NotFoundException(BoardErrorCode.NOT_FOUND)
}
