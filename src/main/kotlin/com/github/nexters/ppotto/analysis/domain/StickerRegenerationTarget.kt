package com.github.nexters.ppotto.analysis.domain

import com.github.nexters.ppotto.global.identifier.PhotoId

data class StickerRegenerationTarget(
    val stickerTargetSubject: String,
    val stickerSourcePhotoId: PhotoId,
    val stickerMainColor: String,
)
