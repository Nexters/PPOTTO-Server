package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.port.BoardStickerItem
import com.github.nexters.ppotto.board.application.port.BoardStickerQueryPort
import com.github.nexters.ppotto.board.domain.Board
import com.github.nexters.ppotto.board.domain.Drawing
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.infrastructure.DrawingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class BoardQueryService(
    private val boardAccessService: BoardAccessService,
    private val boardRepository: BoardRepository,
    private val drawingRepository: DrawingRepository,
    private val stickerQueryPort: BoardStickerQueryPort,
) {
    @Transactional(readOnly = true)
    fun list(userId: UUID): List<BoardSummary> = boardRepository.findByUserId(userId).map(BoardSummary::from)

    @Transactional(readOnly = true)
    fun getDetail(
        boardId: UUID,
        userId: UUID,
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
    val id: UUID,
    val name: String,
) {
    companion object {
        fun from(board: Board): BoardSummary = BoardSummary(board.id, board.name)
    }
}

data class BoardDetail(
    val id: UUID,
    val name: String,
    val stickers: List<BoardStickerItem>,
    val drawings: List<Drawing>,
)
