package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.board.application.BoardShareQueryService
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.sticker.application.model.StickerRecapResult
import org.springframework.stereotype.Service

@Service
class SharedBoardRecapQueryService(
    private val boardShareQueryService: BoardShareQueryService,
    private val stickerQueryService: StickerQueryService,
) {
    fun get(
        shareToken: String,
        stickerId: StickerId,
    ): StickerRecapResult =
        boardShareQueryService.getRecap(shareToken) { access ->
            stickerQueryService.findRecapInBoard(access.boardId, stickerId, access.includesPhotos)
        }
}
