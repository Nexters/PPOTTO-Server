package com.github.nexters.ppotto.analysis.domain

import com.github.nexters.ppotto.global.identifier.PhotoId

data class ThemeClassification(
    val theme: String,
    val categorizedPhotoIds: List<PhotoId>,
    val recap: RecapContent,
    val stickerTargetSubject: String,
    val stickerSourcePhotoId: PhotoId,
    val stickerMainColor: String,
    val comments: List<ThemeComment>,
)
