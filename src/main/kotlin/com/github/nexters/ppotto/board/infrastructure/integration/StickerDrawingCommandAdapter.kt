package com.github.nexters.ppotto.board.infrastructure.integration

import com.github.nexters.ppotto.board.infrastructure.DrawingRepository
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.sticker.application.port.StickerDrawingCommandPort
import org.springframework.stereotype.Component

@Component
class StickerDrawingCommandAdapter(
    private val drawingRepository: DrawingRepository,
) : StickerDrawingCommandPort {
    override fun deleteByStickerIds(
        boardId: BoardId,
        stickerIds: Collection<StickerId>,
    ) {
        drawingRepository.softDeleteByStickerIds(boardId, stickerIds)
    }
}
