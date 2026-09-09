package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.port.BoardStickerQueryPort
import com.github.nexters.ppotto.board.domain.Board
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.infrastructure.DrawingRepository
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import org.springframework.stereotype.Service

@Service
class BoardQueryService(
    private val boardAccessService: BoardAccessService,
    private val boardRepository: BoardRepository,
    private val drawingRepository: DrawingRepository,
    private val stickerQueryPort: BoardStickerQueryPort,
) {
    fun list(userId: UserId): List<Board> = boardRepository.findByUserId(userId)

    fun getDetail(
        boardId: BoardId,
        userId: UserId,
    ): BoardDetail {
        val board = boardAccessService.getOwnedById(boardId, userId)
        return BoardDetail(
            id = board.id,
            name = board.name,
            stickers = stickerQueryPort.getByBoardId(boardId),
            drawings = drawingRepository.findByBoardId(boardId),
        )
    }
}
