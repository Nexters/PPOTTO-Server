package com.github.nexters.ppotto.board.infrastructure

import com.github.nexters.ppotto.board.application.BoardDrawingCommandService
import com.github.nexters.ppotto.sticker.application.port.StickerDrawingCommandPort
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class StickerDrawingCommandAdapter(
    private val drawingCommandService: BoardDrawingCommandService,
) : StickerDrawingCommandPort {
    override fun deleteByStickerIds(
        boardId: UUID,
        stickerIds: Collection<UUID>,
    ) {
        drawingCommandService.deleteByStickerIds(boardId, stickerIds)
    }
}
