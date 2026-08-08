package com.github.nexters.ppotto.sticker.presentation.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.sticker.application.StickerItemResult
import com.github.nexters.ppotto.sticker.domain.StickerType
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "스티커 내용과 보드 배치")
data class StickerResponse(
    @get:Schema(description = "스티커 ID (uuidv7)", example = "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f")
    @get:JsonProperty("id")
    val id: StickerId,

    @field:Schema(description = "제목 뱃지 문구이자 리캡 제목", example = "동물 밈 짤줍")
    val title: String,

    @get:Schema(description = "미열람 여부. 뱃지에 빨간 점 표시", example = "false")
    @get:JsonProperty("isNew")
    val isNew: Boolean,

    @field:Schema(description = "스티커 형식", example = "IMAGE")
    val type: StickerType,

    @field:Schema(
        description = "IMAGE 형의 누끼 PNG 읽기용 signed URL (만료 1시간)",
        example = "https://storage.googleapis.com/ppotto-stickers/01983f2b.png?X-Goog-Signature=sample",
    )
    val imageUrl: String?,

    @field:Schema(description = "TEXT 형 문구", example = "whats in my mac")
    val textContent: String?,

    @field:Schema(description = "보드 좌표 X. null이면 클라이언트가 아직 배치를 정하지 않은 스티커. 보드 상태를 보고 계산해 레이아웃 수정 API로 채워야 함", example = "62.5")
    val posX: Double?,

    @field:Schema(description = "보드 좌표 Y. null이면 클라이언트가 아직 배치를 정하지 않은 스티커. 보드 상태를 보고 계산해 레이아웃 수정 API로 채워야 함", example = "318")
    val posY: Double?,

    @field:Schema(description = "확대 비율. 1.0이 원본 크기 기준이며 0.8=80% 축소, 1.1=110% 확대처럼 사용. 0보다 큰 값만 허용", example = "0.8")
    val scale: Double,

    @field:Schema(description = "회전 각도(degree)", example = "-12")
    val rotation: Double,

    @get:Schema(description = "겹침 순서. null이면 클라이언트가 아직 배치를 정하지 않은 스티커. 보드 상태를 보고 계산해 레이아웃 수정 API로 채워야 함", example = "3")
    @get:JsonProperty("zIndex")
    val zIndex: Int?,

    @field:Schema(description = "스티커 기준 뱃지 상대 좌표 X", example = "-24")
    val badgeOffsetX: Double,

    @field:Schema(description = "스티커 기준 뱃지 상대 좌표 Y", example = "96")
    val badgeOffsetY: Double,

    @field:Schema(description = "뱃지 회전 각도(degree)", example = "0")
    val badgeRotation: Double,
) {
    companion object {
        fun from(result: StickerItemResult) =
            StickerResponse(
                id = result.id,
                title = result.title,
                isNew = result.isNew,
                type = result.type,
                imageUrl = result.imageUrl,
                textContent = result.textContent,
                posX = result.posX,
                posY = result.posY,
                scale = result.scale,
                rotation = result.rotation,
                zIndex = result.zIndex,
                badgeOffsetX = result.badgeOffsetX,
                badgeOffsetY = result.badgeOffsetY,
                badgeRotation = result.badgeRotation,
            )
    }
}
