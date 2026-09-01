package com.github.nexters.ppotto.board.presentation.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.github.nexters.ppotto.board.application.BoardLayoutUpdateCommand
import com.github.nexters.ppotto.board.application.DrawingCreateCommand
import com.github.nexters.ppotto.board.domain.Drawing
import com.github.nexters.ppotto.board.domain.DrawingScope
import com.github.nexters.ppotto.global.identifier.DrawingId
import com.github.nexters.ppotto.global.identifier.StickerId
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

const val DRAWING_COLOR_PATTERN = "^#[0-9A-Fa-f]{6}$"

@Schema(description = "보드 편집 결과 일괄 저장 요청 (v2). 편집 모드에서 바뀐 것만 보냄")
data class BoardLayoutV2Request(
    @field:Schema(description = "변경된 스티커 배치. v1과 동일")
    val stickers: List<@Valid StickerLayoutRequest>? = null,

    @field:Valid
    @field:Schema(description = "선과 텍스트의 생성·삭제 변경분")
    val drawings: DrawingChangesV2Request? = null,
) {
    fun toCommand(): BoardLayoutUpdateCommand =
        BoardLayoutUpdateCommand(
            stickers = stickers.orEmpty().map(StickerLayoutRequest::toCommand),
            createdDrawings =
                drawings
                    ?.created
                    .orEmpty()
                    .map(DrawingCreateV2Request::toCommand),
            deletedDrawingIds = drawings?.deletedIds.orEmpty(),
        )
}

@Schema(description = "선과 텍스트의 생성·삭제 변경분")
data class DrawingChangesV2Request(
    @field:Schema(
        description = "새로 만든 선과 텍스트. 클라이언트가 만든 id로 upsert하므로 재시도해도 멱등이고, 같은 id를 다시 보내면 수정이 된다",
    )
    val created: List<@Valid DrawingCreateV2Request>? = null,

    @field:Schema(
        description = "삭제할 그림 ID 목록. 선과 텍스트를 구분하지 않는다",
        example = "[\"01983f2c-2b3c-7d4e-9f5a-6b7c8d9e0f1a\"]",
    )
    val deletedIds: List<DrawingId>? = null,
)

@Schema(
    description = "새 선 또는 텍스트. type이 판별자다",
    discriminatorProperty = "type",
    oneOf = [DrawingCreateV2Request.Stroke::class, DrawingCreateV2Request.Text::class],
)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = DrawingCreateV2Request.Stroke::class, name = "STROKE"),
    JsonSubTypes.Type(value = DrawingCreateV2Request.Text::class, name = "TEXT"),
)
sealed interface DrawingCreateV2Request {
    val id: DrawingId
    val scope: DrawingScope
    val stickerId: StickerId?
    val color: String
    val zIndex: Int

    fun toCommand(): DrawingCreateCommand

    @Schema(name = "DrawingCreateStrokeRequest", description = "새 선")
    data class Stroke(
        @get:Schema(
            description = "클라이언트가 생성한 uuidv7. 서버가 이 id로 upsert함",
            example = "01983f2c-3c4d-7e5f-a6b7-8c9d0e1f2a3b",
        )
        @get:JsonProperty("id")
        override val id: DrawingId,

        @field:Schema(description = "선이 붙는 대상", example = "STICKER")
        override val scope: DrawingScope,

        @get:Schema(description = "scope=STICKER일 때 필수", example = "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f")
        @get:JsonProperty("stickerId")
        override val stickerId: StickerId? = null,

        @field:NotBlank
        @field:Pattern(regexp = DRAWING_COLOR_PATTERN)
        @field:Schema(description = "선 색상. #RRGGBB", example = "#FFD400")
        override val color: String,

        @get:Schema(description = "겹침 순서. 스티커 zIndex와 같은 숫자 공간을 쓴다", example = "6")
        @get:JsonProperty("zIndex")
        override val zIndex: Int,

        @field:NotEmpty
        @field:Schema(
            description = "선 데이터. 포맷은 클라이언트 정의를 그대로 저장",
            example = "{\"points\":[[10.5,22],[14.2,25.1],[19.8,27.4]]}",
        )
        val stroke: Map<String, Any?>,

        @field:Positive
        @field:Schema(description = "선 굵기", example = "4")
        val strokeWidth: Double,
    ) : DrawingCreateV2Request {
        override fun toCommand(): DrawingCreateCommand =
            DrawingCreateCommand.Stroke(
                id = id,
                scope = scope,
                stickerId = stickerId,
                color = color,
                zIndex = zIndex,
                stroke = stroke,
                strokeWidth = strokeWidth,
            )
    }

    @Schema(name = "DrawingCreateTextRequest", description = "새 텍스트")
    data class Text(
        @get:Schema(
            description = "클라이언트가 생성한 uuidv7. 서버가 이 id로 upsert함",
            example = "01983f2c-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
        )
        @get:JsonProperty("id")
        override val id: DrawingId,

        @field:Schema(description = "텍스트가 붙는 대상", example = "BOARD")
        override val scope: DrawingScope,

        @get:Schema(description = "scope=STICKER일 때 필수", example = "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f")
        @get:JsonProperty("stickerId")
        override val stickerId: StickerId? = null,

        @field:NotBlank
        @field:Pattern(regexp = DRAWING_COLOR_PATTERN)
        @field:Schema(description = "글자 색상. #RRGGBB", example = "#FFFFFF")
        override val color: String,

        @get:Schema(description = "겹침 순서. 스티커 zIndex와 같은 숫자 공간을 쓴다", example = "7")
        @get:JsonProperty("zIndex")
        override val zIndex: Int,

        @field:NotBlank
        @field:Size(max = Drawing.Text.MAX_CONTENT_LENGTH)
        @field:Schema(
            description = "표시할 문구. 최대 32자. 클라이언트가 실측한 줄바꿈이 그대로 들어오며 서버는 다시 감싸지 않음",
            example = "여름 휴가",
        )
        val content: String,

        @field:Positive
        @field:Schema(description = "글자 크기", example = "26")
        val fontSize: Double,

        @field:Schema(description = "보드 좌표 X. 텍스트 상자의 중심", example = "80")
        val posX: Double,

        @field:Schema(description = "보드 좌표 Y. 텍스트 상자의 중심", example = "290.5")
        val posY: Double,

        @field:Positive
        @field:Schema(description = "줄바꿈 기준이 된 편집창 폭. 렌더 폭 보존용", example = "280")
        val maxWidth: Double,

        @field:Schema(description = "회전 각도(degree)", example = "0")
        val rotation: Double = 0.0,
    ) : DrawingCreateV2Request {
        override fun toCommand(): DrawingCreateCommand =
            DrawingCreateCommand.Text(
                id = id,
                scope = scope,
                stickerId = stickerId,
                color = color,
                zIndex = zIndex,
                content = content,
                fontSize = fontSize,
                posX = posX,
                posY = posY,
                maxWidth = maxWidth,
                rotation = rotation,
            )
    }
}
