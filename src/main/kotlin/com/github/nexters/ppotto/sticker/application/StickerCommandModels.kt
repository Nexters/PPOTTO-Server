package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.sticker.domain.StickerLayout

data class StickerTitleResult(
    val id: StickerId,
    val title: String,
)

data class StickerLayoutCommand(
    val id: StickerId,
    val layout: StickerLayout,
)
