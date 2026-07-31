package com.github.nexters.ppotto.sticker.presentation.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.nexters.ppotto.sticker.application.StickerRecapResult
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

@Schema(description = "스티커와 분석 리캡")
data class RecapDetailResponse(
    @field:Schema(description = "리캡 대상 스티커")
    val sticker: StickerResponse,
    @field:Schema(description = "분석 코멘트. id(uuidv7) 오름차순")
    val comments: List<RecapCommentResponse>,
    @field:Schema(description = "리캡 사진. takenAt, id 오름차순")
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
    @field:Schema(description = "코멘트 ID (uuidv7)", example = "01983f2d-1a2b-7c3d-8e4f-5a6b7c8d9e0f")
    val id: UUID,
    @field:Schema(description = "코멘트 문구", example = "웃기고 귀여우면 일단 주워요")
    val content: String,
    @get:Schema(
        description = "true면 스티커 주변 말풍선, false면 하단 순차 노출",
        example = "true",
    )
    @get:JsonProperty("isFloat")
    val isFloat: Boolean,
    @field:Schema(description = "isFloat일 때 스티커 기준 상대 좌표 X", example = "0")
    val posX: Double?,
    @field:Schema(description = "isFloat일 때 스티커 기준 상대 좌표 Y", example = "-140")
    val posY: Double?,
)

@Schema(description = "분석 리캡 사진")
data class RecapPhotoResponse(
    @field:Schema(description = "사진 ID (uuidv7)", example = "01983f2e-1a2b-7c3d-8e4f-5a6b7c8d9e0f")
    val id: UUID,
    @field:Schema(
        description = "읽기용 signed URL (만료 1시간)",
        example = "https://storage.googleapis.com/ppotto-photos/01983f2e.jpg?X-Goog-Signature=sample",
    )
    val imageUrl: String,
    @field:Schema(description = "촬영 시각", example = "2026-06-14T13:22:10+09:00")
    val takenAt: Instant,
)
