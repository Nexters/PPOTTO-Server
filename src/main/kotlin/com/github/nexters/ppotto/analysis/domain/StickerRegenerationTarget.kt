package com.github.nexters.ppotto.analysis.domain

import java.util.UUID

data class StickerRegenerationTarget(
    val stickerTargetSubject: String,
    val stickerSourcePhotoId: UUID,
    val stickerMainColor: String,
)
