package com.github.nexters.ppotto.sticker.application.port

import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.StickerId

interface StickerDrawingCommandPort {
    fun deleteByStickerIds(
        boardId: BoardId,
        stickerIds: Collection<StickerId>,
    )
}
