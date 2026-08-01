package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.domain.Board
import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class BoardAccessService(
    private val boardRepository: BoardRepository,
) {
    @Transactional(readOnly = true)
    fun getById(id: BoardId): Board =
        boardRepository.findById(id)
            ?: throw NotFoundException(BoardErrorCode.NOT_FOUND)

    @Transactional(readOnly = true)
    fun getOwnedById(
        boardId: BoardId,
        userId: UserId,
    ): Board =
        boardRepository.findOwnedById(boardId, userId)
            ?: throw NotFoundException(BoardErrorCode.NOT_FOUND)

    @Transactional(propagation = Propagation.MANDATORY)
    fun getOwnedByIdForUpdate(
        boardId: BoardId,
        userId: UserId,
    ): Board =
        boardRepository.findOwnedByIdForUpdate(boardId, userId)
            ?: throw NotFoundException(BoardErrorCode.NOT_FOUND)
}
