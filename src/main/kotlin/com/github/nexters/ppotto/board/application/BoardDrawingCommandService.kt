package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.infrastructure.DrawingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class BoardDrawingCommandService(
    private val drawingRepository: DrawingRepository,
) {
    @Transactional
    fun deleteByStickerIds(
        boardId: UUID,
        stickerIds: Collection<UUID>,
    ) {
        drawingRepository.softDeleteByStickerIds(boardId, stickerIds)
    }

    @Transactional
    fun deleteAllByBoardId(boardId: UUID) {
        drawingRepository.softDeleteAllByBoardId(boardId)
    }
}
