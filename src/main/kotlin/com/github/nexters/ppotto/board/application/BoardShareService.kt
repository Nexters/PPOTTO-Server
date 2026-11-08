package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.model.BoardShareResult
import com.github.nexters.ppotto.board.application.model.BoardShareStatus
import com.github.nexters.ppotto.board.domain.BoardShare
import com.github.nexters.ppotto.board.domain.BoardShareScope
import com.github.nexters.ppotto.board.infrastructure.BoardShareRepository
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class BoardShareService(
    private val boardAccessService: BoardAccessService,
    private val boardShareRepository: BoardShareRepository,
) {
    @Transactional
    fun share(
        boardId: BoardId,
        userId: UserId,
        scope: BoardShareScope,
    ): BoardShareResult {
        boardAccessService.getReadableByIdForUpdate(boardId, userId)
        val existing = boardShareRepository.findByBoardIdAndSharerUserIdForUpdate(boardId, userId)
        val share =
            if (existing == null) {
                boardShareRepository.save(boardId, userId, UUID.randomUUID().toString(), scope)
            } else {
                boardShareRepository.updateScope(boardId, userId, scope)
            }
        return share.toResult()
    }

    fun getStatus(
        boardId: BoardId,
        userId: UserId,
    ): BoardShareStatus {
        boardAccessService.getReadableById(boardId, userId)
        val share = boardShareRepository.findByBoardIdAndSharerUserId(boardId, userId)
        return BoardShareStatus(
            shareToken = share?.shareToken,
            scope = share?.scope ?: BoardShareScope.DEFAULT,
        )
    }

    private fun BoardShare.toResult() = BoardShareResult(shareToken = shareToken, scope = scope)
}
