package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.port.BoardStickerItem
import com.github.nexters.ppotto.board.application.port.BoardStickerQueryPort
import com.github.nexters.ppotto.board.domain.Board
import com.github.nexters.ppotto.board.domain.Drawing
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.infrastructure.DrawingRepository
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BoardQueryService(
    private val boardAccessService: BoardAccessService,
    private val boardRepository: BoardRepository,
    private val drawingRepository: DrawingRepository,
    private val stickerQueryPort: BoardStickerQueryPort,
) {
    @Transactional(readOnly = true)
    fun list(userId: UserId): List<BoardSummary> = boardRepository.findByUserId(userId).map(BoardSummary::from)

    fun getDetail(
        boardId: BoardId,
        userId: UserId,
    ): BoardDetail =
        boardAccessService.getOwnedById(boardId, userId).let {
            BoardDetail(
                id = it.id,
                name = it.name,
                stickers = stickerQueryPort.getByBoardId(boardId),
                drawings = drawingRepository.findByBoardId(boardId),
            )
        }
}

data class BoardSummary(
    val id: BoardId,
    val name: String,
) {
    companion object {
        fun from(board: Board): BoardSummary = BoardSummary(board.id, board.name)
    }
}

data class BoardDetail(
    val id: BoardId,
    val name: String,
    val stickers: List<BoardStickerItem>,
    val drawings: List<Drawing>,
)
