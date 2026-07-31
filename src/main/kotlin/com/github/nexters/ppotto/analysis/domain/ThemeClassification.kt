package com.github.nexters.ppotto.analysis.domain

import java.util.UUID

data class ThemeClassification(
    val theme: String,
    val categorizedPhotoIds: List<UUID>,
    val recap: RecapContent,
    val stickerTargetSubject: String,
    val stickerSourcePhotoId: UUID,
)
