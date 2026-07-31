package com.github.nexters.ppotto.sticker.support

import com.github.nexters.ppotto.sticker.application.port.StickerDrawingCommandPort
import java.util.UUID

class FakeStickerDrawingCommandPort : StickerDrawingCommandPort {
    val deletedStickerIds = mutableListOf<UUID>()

    override fun deleteByStickerIds(
        boardId: UUID,
        stickerIds: Collection<UUID>,
    ) {
        deletedStickerIds += stickerIds
    }
}
