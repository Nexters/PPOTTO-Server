package com.github.nexters.ppotto.board.presentation.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.nexters.ppotto.board.application.BoardLayoutService
import com.github.nexters.ppotto.board.application.BoardLayoutUpdateCommand
import com.github.nexters.ppotto.board.application.DrawingCreateCommand
import com.github.nexters.ppotto.board.application.port.BoardStickerLayoutCommand
import com.github.nexters.ppotto.board.domain.DrawingScope
import com.github.nexters.ppotto.global.identifier.DrawingId
import com.github.nexters.ppotto.global.identifier.StickerId
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

@Schema(description = "보드 편집 결과 일괄 저장 요청. 편집 모드에서 바뀐 것만 보냄")
data class BoardLayoutRequest(
    @field:Schema(description = "변경된 스티커 배치")
    val stickers: List<@Valid StickerLayoutRequest>? = null,

    @field:Valid
    @field:Schema(description = "그림 생성과 삭제 변경분")
    val drawings: DrawingChangesRequest? = null,
) {
    fun toCommand(): BoardLayoutUpdateCommand =
        BoardLayoutUpdateCommand(
            stickers = stickers.orEmpty().map(StickerLayoutRequest::toCommand),
            createdDrawings =
                drawings
                    ?.created
                    .orEmpty()
                    .map(DrawingCreateRequest::toCommand),
            deletedDrawingIds = drawings?.deletedIds.orEmpty(),
        )
}

@Schema(description = "스티커 배치와 제목")
data class StickerLayoutRequest(
    @get:Schema(description = "스티커 ID (uuidv7)", example = "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f")
    @get:JsonProperty("id")
    val id: StickerId,

    @field:Size(min = 1, max = BoardLayoutService.MAX_STICKER_TITLE_LENGTH)
    @field:Schema(description = "텍스트 모드에서 제목을 바꿨을 때만 보냄. 최대 15자", example = "고양이 모음집")
    val title: String? = null,

    @field:Schema(description = "보드 좌표 X", example = "80")
    val posX: Double,

    @field:Schema(description = "보드 좌표 Y", example = "290.5")
    val posY: Double,

    @field:Schema(description = "확대 비율. 1.0이 원본 크기 기준이며 0.8=80% 축소, 1.1=110% 확대처럼 사용. 0보다 큰 값만 허용", example = "1.1")
    val scale: Double,

    @field:Schema(description = "회전 각도(degree)", example = "-8")
    val rotation: Double,

    @get:Schema(description = "겹침 순서", example = "6")
    @get:JsonProperty("zIndex")
    val zIndex: Int,

    @field:Schema(description = "스티커 기준 뱃지 상대 좌표 X", example = "-24")
    val badgeOffsetX: Double,

    @field:Schema(description = "스티커 기준 뱃지 상대 좌표 Y", example = "96")
    val badgeOffsetY: Double,

    @field:Schema(description = "뱃지 회전 각도(degree)", example = "0")
    val badgeRotation: Double,
) {
    fun toCommand(): BoardStickerLayoutCommand =
        BoardStickerLayoutCommand(
            id = id,
            title = title,
            posX = posX,
            posY = posY,
            scale = scale,
            rotation = rotation,
            zIndex = zIndex,
            badgeOffsetX = badgeOffsetX,
            badgeOffsetY = badgeOffsetY,
            badgeRotation = badgeRotation,
        )
}

@Schema(description = "그림 생성과 삭제 변경분")
data class DrawingChangesRequest(
    @field:Schema(description = "새로 그린 선. 클라이언트가 만든 id로 upsert하므로 재시도해도 멱등")
    val created: List<@Valid DrawingCreateRequest>? = null,

    @field:Schema(
        description = "삭제할 그림 ID 목록",
        example = "[\"01983f2c-2b3c-7d4e-9f5a-6b7c8d9e0f1a\"]",
    )
    val deletedIds: List<DrawingId>? = null,
)

@Schema(description = "새 그림")
data class DrawingCreateRequest(
    @get:Schema(
        description = "클라이언트가 생성한 uuidv7. 서버가 이 id로 upsert함",
        example = "01983f2c-3c4d-7e5f-a6b7-8c9d0e1f2a3b",
    )
    @get:JsonProperty("id")
    val id: DrawingId,

    @field:Schema(description = "그림이 붙는 대상", example = "STICKER")
    val scope: DrawingScope,

    @get:Schema(description = "scope=STICKER일 때 필수", example = "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f")
    @get:JsonProperty("stickerId")
    val stickerId: StickerId? = null,

    @field:NotEmpty
    @field:Schema(
        description = "선 데이터. 포맷은 클라이언트 정의를 그대로 저장",
        example = "{\"points\":[[10.5,22],[14.2,25.1],[19.8,27.4]]}",
    )
    val stroke: Map<String, Any?>,

    @field:NotBlank
    @field:Schema(description = "선 색상", example = "#FFD400")
    val color: String,

    @field:Positive
    @field:Schema(description = "선 굵기", example = "4")
    val strokeWidth: Double,
) {
    fun toCommand(): DrawingCreateCommand =
        DrawingCreateCommand(
            id = id,
            scope = scope,
            stickerId = stickerId,
            stroke = stroke,
            color = color,
            strokeWidth = strokeWidth,
        )
}
