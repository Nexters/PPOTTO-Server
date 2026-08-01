package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.infrastructure.DrawingRepository
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.StickerId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BoardDrawingCommandService(
    private val drawingRepository: DrawingRepository,
) {
    @Transactional
    fun deleteByStickerIds(
        boardId: BoardId,
        stickerIds: Collection<StickerId>,
    ) {
        drawingRepository.softDeleteByStickerIds(boardId, stickerIds)
    }

    @Transactional
    fun deleteAllByBoardId(boardId: BoardId) {
        drawingRepository.softDeleteAllByBoardId(boardId)
    }
}
