package com.github.nexters.ppotto.board.infrastructure

import com.github.nexters.ppotto.board.application.BoardDrawingCommandService
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.sticker.application.port.StickerDrawingCommandPort
import org.springframework.stereotype.Component

@Component
class StickerDrawingCommandAdapter(
    private val drawingCommandService: BoardDrawingCommandService,
) : StickerDrawingCommandPort {
    override fun deleteByStickerIds(
        boardId: BoardId,
        stickerIds: Collection<StickerId>,
    ): Unit = drawingCommandService.deleteByStickerIds(boardId, stickerIds)
}
