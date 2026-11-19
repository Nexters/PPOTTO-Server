package com.github.nexters.ppotto.sticker.application.model

import com.github.nexters.ppotto.global.identifier.StickerId

class StickerLayoutConflictException(
    val stickerIds: Set<StickerId>,
) : RuntimeException()
