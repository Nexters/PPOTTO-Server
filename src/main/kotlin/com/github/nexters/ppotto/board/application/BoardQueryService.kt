package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.model.BoardDetail
import com.github.nexters.ppotto.board.application.model.BoardSummary
import com.github.nexters.ppotto.board.application.port.BoardActiveAnalysisPort
import com.github.nexters.ppotto.board.application.port.BoardStickerQueryPort
import com.github.nexters.ppotto.board.application.port.BoardStickerSummaryPort
import com.github.nexters.ppotto.board.domain.Board
import com.github.nexters.ppotto.board.domain.BoardKind
import com.github.nexters.ppotto.board.domain.BoardRole
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
    private val stickerSummaryPort: BoardStickerSummaryPort,
    private val activeAnalysisPort: BoardActiveAnalysisPort,
) {
    fun list(userId: UserId): List<Board> = boardRepository.findByUserId(userId)

    fun listSummaries(userId: UserId): List<BoardSummary> {
        val boards = boardRepository.findAccessibleByUserId(userId)
        val stickers =
            stickerSummaryPort.getByBoardIds(
                boards.map { it.board.id }.toSet(),
                BoardSummary.MAX_PREVIEW_IMAGE_COUNT,
            )
        val activeAnalysis = activeAnalysisPort.findByUserId(userId)

        return boards
            .map { (board, memberCount) ->
                val sticker = stickers[board.id]
                BoardSummary(
                    id = board.id,
                    name = board.name,
                    kind = BoardKind.of(memberCount),
                    role = if (board.userId == userId) BoardRole.OWNER else BoardRole.MEMBER,
                    stickerCount = sticker?.stickerCount ?: 0,
                    participantCount = memberCount + 1,
                    previewImageUrls = sticker?.previewImageUrls.orEmpty(),
                    mainColor = sticker?.mainColor,
                    lastActivityAt = sticker?.lastChangedAt ?: board.createdAt,
                    analysisStatus = activeAnalysis?.takeIf { it.boardId == board.id }?.status,
                )
            }.sortedWith(compareByDescending<BoardSummary> { it.lastActivityAt }.thenByDescending { it.id.toString() })
    }

    fun getDetail(
        boardId: BoardId,
        userId: UserId,
    ): BoardDetail {
        val board = boardAccessService.getReadableById(boardId, userId)
        return BoardDetail(
            id = board.id,
            name = board.name,
            stickers = stickerQueryPort.getByBoardId(boardId),
            drawings = drawingRepository.findByBoardId(boardId),
        )
    }
}
