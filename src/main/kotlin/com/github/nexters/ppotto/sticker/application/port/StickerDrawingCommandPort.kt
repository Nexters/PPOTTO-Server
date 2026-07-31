package com.github.nexters.ppotto.sticker.application.port

import java.util.UUID

interface StickerDrawingCommandPort {
    fun deleteByStickerIds(
        boardId: UUID,
        stickerIds: Collection<UUID>,
    )
}
