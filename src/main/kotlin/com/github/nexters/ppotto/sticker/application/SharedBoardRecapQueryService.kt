package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.board.application.BoardShareQueryService
import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.global.error.NotFoundException
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
    ): StickerRecapResult {
        val access = boardShareQueryService.getRecapAccess(shareToken)
        return stickerQueryService.findRecapInBoard(access.boardId, stickerId, access.includesPhotos)
            ?: throw NotFoundException(BoardErrorCode.SHARED_RECAP_NOT_FOUND)
    }
}
