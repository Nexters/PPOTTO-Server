package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.domain.Board
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.error.NotFoundException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class BoardQueryService(
    private val boardRepository: BoardRepository,
) {
    fun getById(id: UUID): Board = boardRepository.findById(id) ?: throw NotFoundException()
}
