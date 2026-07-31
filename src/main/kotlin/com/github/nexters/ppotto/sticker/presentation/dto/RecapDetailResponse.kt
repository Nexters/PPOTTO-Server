package com.github.nexters.ppotto.sticker.presentation.dto

import com.github.nexters.ppotto.sticker.application.StickerRecapResult
import java.time.Instant
import java.util.UUID

data class RecapDetailResponse(
    val sticker: StickerResponse,
    val comments: List<RecapCommentResponse>,
    val photos: List<RecapPhotoResponse>,
) {
    companion object {
        fun from(result: StickerRecapResult) =
            RecapDetailResponse(
                sticker = StickerResponse.from(result.sticker),
                comments =
                    result.comments.map {
                        RecapCommentResponse(it.id, it.content, it.isFloat, it.posX, it.posY)
                    },
                photos =
                    result.photos.map {
                        RecapPhotoResponse(it.id, it.imageUrl, it.takenAt)
                    },
            )
    }
}

data class RecapCommentResponse(
    val id: UUID,
    val content: String,
    val isFloat: Boolean,
    val posX: Double?,
    val posY: Double?,
)

data class RecapPhotoResponse(
    val id: UUID,
    val imageUrl: String,
    val takenAt: Instant,
)
