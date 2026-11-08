package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.model.SharedBoard
import com.github.nexters.ppotto.board.application.port.BoardStickerQueryPort
import com.github.nexters.ppotto.board.domain.BoardErrorCode
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
        val share = boardShareRepository.findByShareToken(shareToken) ?: shareNotFound()
        val board = boardMembershipRepository.findReadableById(share.boardId, share.sharerUserId) ?: shareNotFound()
        val sharerName = userService.findActiveName(share.sharerUserId) ?: shareNotFound()

        return SharedBoard(
            boardName = board.name,
            sharerName = sharerName,
            scope = share.scope,
            stickers = stickerQueryPort.getByBoardId(board.id).map { it.copy(isNew = false) },
            drawings = drawingRepository.findByBoardId(board.id),
        )
    }

    private fun shareNotFound(): Nothing = throw NotFoundException(BoardErrorCode.SHARE_NOT_FOUND)
}
