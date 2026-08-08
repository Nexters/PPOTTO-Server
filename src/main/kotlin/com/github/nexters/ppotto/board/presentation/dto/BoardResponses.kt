package com.github.nexters.ppotto.board.presentation.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.nexters.ppotto.board.application.BoardDetail
import com.github.nexters.ppotto.board.application.BoardSummary
import com.github.nexters.ppotto.board.application.port.BoardStickerItem
import com.github.nexters.ppotto.board.domain.Board
import com.github.nexters.ppotto.board.domain.BoardStickerType
import com.github.nexters.ppotto.board.domain.Drawing
import com.github.nexters.ppotto.board.domain.DrawingScope
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.DrawingId
import com.github.nexters.ppotto.global.identifier.StickerId
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "보드 요약")
data class BoardResponse(
    @get:Schema(description = "보드 ID (uuidv7). 오름차순이 생성순", example = "01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b")
    @get:JsonProperty("id")
    val id: BoardId,

    @field:Schema(description = "보드 이름. 최대 10자", example = "여름 휴가")
    val name: String,
) {
    companion object {
        fun from(board: Board): BoardResponse = BoardResponse(board.id, board.name)

        fun from(board: BoardSummary): BoardResponse = BoardResponse(board.id, board.name)
    }
}

@Schema(description = "보드와 배치된 스티커 및 그림")
data class BoardDetailResponse(
    @get:Schema(description = "보드 ID (uuidv7)", example = "01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b")
    @get:JsonProperty("id")
    val id: BoardId,

    @field:Schema(description = "보드 이름", example = "Board 7")
    val name: String,

    @field:Schema(description = "보드에 배치된 스티커 목록")
    val stickers: List<StickerResponse>,

    @field:Schema(description = "보드와 스티커 위의 그림 목록")
    val drawings: List<DrawingResponse>,
) {
    companion object {
        fun from(board: BoardDetail): BoardDetailResponse =
            BoardDetailResponse(
                id = board.id,
                name = board.name,
                stickers = board.stickers.map(StickerResponse::from),
                drawings = board.drawings.map(DrawingResponse::from),
            )
    }
}

@Schema(name = "BoardStickerResponse", description = "보드에 배치된 스티커")
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
    val type: BoardStickerType,

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
        fun from(sticker: BoardStickerItem): StickerResponse =
            StickerResponse(
                id = sticker.id,
                title = sticker.title,
                isNew = sticker.isNew,
                type = sticker.type,
                imageUrl = sticker.imageUrl,
                textContent = sticker.textContent,
                posX = sticker.posX,
                posY = sticker.posY,
                scale = sticker.scale,
                rotation = sticker.rotation,
                zIndex = sticker.zIndex,
                badgeOffsetX = sticker.badgeOffsetX,
                badgeOffsetY = sticker.badgeOffsetY,
                badgeRotation = sticker.badgeRotation,
            )
    }
}

@Schema(description = "보드 또는 스티커 위의 그림")
data class DrawingResponse(
    @get:Schema(description = "그림 ID (uuidv7)", example = "01983f2c-1a2b-7c3d-8e4f-5a6b7c8d9e0f")
    @get:JsonProperty("id")
    val id: DrawingId,

    @field:Schema(description = "그림이 붙는 대상", example = "STICKER")
    val scope: DrawingScope,

    @get:Schema(description = "scope=STICKER일 때만 값이 있음", example = "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f")
    @get:JsonProperty("stickerId")
    val stickerId: StickerId?,

    @field:Schema(
        description = "선 데이터. 포맷은 클라이언트 정의를 그대로 저장",
        example = "{\"points\":[[10.5,22],[14.2,25.1],[19.8,27.4]]}",
    )
    val stroke: Map<String, Any?>,

    @field:Schema(description = "선 색상", example = "#FFD400")
    val color: String,

    @field:Schema(description = "선 굵기", example = "4")
    val strokeWidth: Double,
) {
    companion object {
        fun from(drawing: Drawing): DrawingResponse =
            DrawingResponse(
                id = drawing.id,
                scope = drawing.scope,
                stickerId = drawing.stickerId,
                stroke = drawing.stroke,
                color = drawing.color,
                strokeWidth = drawing.strokeWidth,
            )
    }
}
