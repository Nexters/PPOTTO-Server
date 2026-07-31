package com.github.nexters.ppotto.sticker.presentation.dto

import com.github.nexters.ppotto.sticker.application.StickerRecapResult
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

@Schema(description = "스티커와 분석 리캡")
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

@Schema(description = "분석 리캡 코멘트")
data class RecapCommentResponse(
    val id: UUID,
    val content: String,
    val isFloat: Boolean,
    val posX: Double?,
    val posY: Double?,
)

@Schema(description = "분석 리캡 사진")
data class RecapPhotoResponse(
    val id: UUID,
    val imageUrl: String,
    val takenAt: Instant,
)
