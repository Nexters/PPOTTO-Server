package com.github.nexters.ppotto.sticker.presentation.dto

import com.github.nexters.ppotto.sticker.application.StickerRecapResult
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

@Schema(description = "스티커와 분석 리캡")
data class RecapDetailResponse(
    @field:Schema(description = "리캡 대상 스티커")
    val sticker: StickerResponse,
    @field:Schema(description = "한 줄 요약. 스티커당 1개인 강조 문장", example = "웃기고 귀여우면 일단 주워요")
    val summary: String,
    @field:Schema(description = "분석 코멘트. id(uuidv7) 오름차순")
    val comments: List<RecapCommentResponse>,
    @field:Schema(description = "리캡 사진. takenAt, id 오름차순")
    val photos: List<RecapPhotoResponse>,
) {
    companion object {
        fun from(result: StickerRecapResult) =
            RecapDetailResponse(
                sticker = StickerResponse.from(result.sticker),
                summary = result.summary,
                comments =
                    result.comments.map {
                        RecapCommentResponse(it.id, it.content, it.posX, it.posY)
                    },
                photos =
                    result.photos.map {
                        RecapPhotoResponse(it.id, it.imageUrl, it.takenAt)
                    },
            )
    }
}

@Schema(description = "분석 리캡 코멘트. posX, posY가 있으면 스티커 주변 말풍선, null이면 하단 키워드 칩")
data class RecapCommentResponse(
    @field:Schema(description = "코멘트 ID (uuidv7)", example = "01983f2d-1a2b-7c3d-8e4f-5a6b7c8d9e0f")
    val id: UUID,
    @field:Schema(description = "코멘트 문구", example = "야옹~")
    val content: String,
    @field:Schema(description = "스티커 기준 상대 좌표 X. null이면 하단 키워드 칩", example = "0")
    val posX: Double?,
    @field:Schema(description = "스티커 기준 상대 좌표 Y. posX와 항상 함께 null이거나 함께 채워진다", example = "-140")
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
