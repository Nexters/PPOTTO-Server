package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.model.SharedBoard
import com.github.nexters.ppotto.board.application.model.SharedBoardRecapAccess
import com.github.nexters.ppotto.board.application.port.BoardStickerQueryPort
import com.github.nexters.ppotto.board.domain.Board
import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.domain.BoardShare
import com.github.nexters.ppotto.board.infrastructure.BoardMembershipRepository
import com.github.nexters.ppotto.board.infrastructure.BoardShareRepository
import com.github.nexters.ppotto.board.infrastructure.DrawingRepository
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.user.application.UserService
import org.springframework.stereotype.Service

@Service
class BoardShareQueryService(
    private val boardShareRepository: BoardShareRepository,
    private val boardMembershipRepository: BoardMembershipRepository,
    private val drawingRepository: DrawingRepository,
    private val stickerQueryPort: BoardStickerQueryPort,
    private val userService: UserService,
) {
    fun getSharedBoard(shareToken: String): SharedBoard {
        val (share, board, sharerName) = resolve(shareToken)

        return SharedBoard(
            boardName = board.name,
            sharerName = sharerName,
            scope = share.scope,
            stickers = stickerQueryPort.getByBoardId(board.id).map { it.copy(isNew = false) },
            drawings = drawingRepository.findByBoardId(board.id),
        )
    }

    fun <T : Any> getRecap(
        shareToken: String,
        findRecap: (SharedBoardRecapAccess) -> T?,
    ): T = findRecap(getRecapAccess(shareToken)) ?: throw NotFoundException(BoardErrorCode.SHARED_RECAP_NOT_FOUND)

    fun getRecapAccess(shareToken: String): SharedBoardRecapAccess {
        val (share, board) = resolve(shareToken)
        if (!share.scope.includesRecap) {
            throw NotFoundException(BoardErrorCode.SHARED_RECAP_NOT_FOUND)
        }
        return SharedBoardRecapAccess(boardId = board.id, includesPhotos = share.scope.includesPhotos)
    }

    private fun resolve(shareToken: String): ResolvedShare {
        val share = boardShareRepository.findByShareToken(shareToken) ?: shareNotFound()
        val board = boardMembershipRepository.findReadableById(share.boardId, share.sharerUserId) ?: shareNotFound()
        val sharerName = userService.findActiveName(share.sharerUserId) ?: shareNotFound()
        return ResolvedShare(share, board, sharerName)
    }

    private fun shareNotFound(): Nothing = throw NotFoundException(BoardErrorCode.SHARE_NOT_FOUND)
}

private data class ResolvedShare(
    val share: BoardShare,
    val board: Board,
    val sharerName: String,
)
