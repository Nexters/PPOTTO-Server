package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.sticker.domain.StickerType
import java.time.Instant
import java.util.UUID

data class StickerItemResult(
    val id: StickerId,
    val title: String,
    val isNew: Boolean,
    val type: StickerType,
    val imageUrl: String?,
    val textContent: String?,
    val posX: Double,
    val posY: Double,
    val scale: Double,
    val rotation: Double,
    val zIndex: Int,
    val badgeOffsetX: Double,
    val badgeOffsetY: Double,
    val badgeRotation: Double,
)

data class StickerRecapResult(
    val sticker: StickerItemResult,
    val summary: String,
    val comments: List<RecapCommentResult>,
    val photos: List<RecapPhotoResult>,
)

data class RecapCommentResult(
    val id: UUID,
    val content: String,
    val posX: Double?,
    val posY: Double?,
)

data class RecapPhotoResult(
    val id: PhotoId,
    val imageUrl: String,
    val takenAt: Instant,
)
