package com.github.nexters.ppotto.board.presentation.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.github.nexters.ppotto.board.application.BoardDetail
import com.github.nexters.ppotto.board.domain.Drawing
import com.github.nexters.ppotto.board.domain.DrawingScope
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.DrawingId
import com.github.nexters.ppotto.global.identifier.StickerId
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "보드와 배치된 스티커, 선, 텍스트 (v2)")
data class BoardDetailV2Response(
    @get:Schema(description = "보드 ID (uuidv7)", example = "01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b")
    @get:JsonProperty("id")
    val id: BoardId,

    @field:Schema(description = "보드 이름", example = "Board 7")
    val name: String,

    @field:Schema(description = "보드에 배치된 스티커 목록. v1과 동일")
    val stickers: List<StickerResponse>,

    @field:Schema(description = "보드와 스티커 위의 선과 텍스트 목록")
    val drawings: List<DrawingV2Response>,
) {
    companion object {
        fun from(board: BoardDetail): BoardDetailV2Response =
            BoardDetailV2Response(
                id = board.id,
                name = board.name,
                stickers = board.stickers.map(StickerResponse::from),
                drawings = board.drawings.map(DrawingV2Response::from),
            )
    }
}

@Schema(
    description = "보드 또는 스티커 위의 선이나 텍스트. type이 판별자다",
    discriminatorProperty = "type",
    oneOf = [DrawingV2Response.Stroke::class, DrawingV2Response.Text::class],
)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = DrawingV2Response.Stroke::class, name = "STROKE"),
    JsonSubTypes.Type(value = DrawingV2Response.Text::class, name = "TEXT"),
)
sealed interface DrawingV2Response {
    val id: DrawingId
    val scope: DrawingScope
    val stickerId: StickerId?
    val color: String
    val zIndex: Int

    @Schema(name = "DrawingStrokeResponse", description = "선")
    data class Stroke(
        @get:Schema(description = "그림 ID (uuidv7)", example = "01983f2c-1a2b-7c3d-8e4f-5a6b7c8d9e0f")
        @get:JsonProperty("id")
        override val id: DrawingId,

        @field:Schema(description = "선이 붙는 대상", example = "STICKER")
        override val scope: DrawingScope,

        @get:Schema(description = "scope=STICKER일 때만 값이 있음", example = "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f")
        @get:JsonProperty("stickerId")
        override val stickerId: StickerId?,

        @field:Schema(description = "선 색상", example = "#FFD400")
        override val color: String,

        @get:Schema(description = "겹침 순서", example = "6")
        @get:JsonProperty("zIndex")
        override val zIndex: Int,

        @field:Schema(
            description = "선 데이터. 저장한 그대로 내려감",
            example = "{\"points\":[[10.5,22],[14.2,25.1],[19.8,27.4]]}",
        )
        val stroke: Map<String, Any?>,

        @field:Schema(description = "선 굵기", example = "4")
        val strokeWidth: Double,
    ) : DrawingV2Response

    @Schema(name = "DrawingTextResponse", description = "텍스트")
    data class Text(
        @get:Schema(description = "그림 ID (uuidv7)", example = "01983f2c-5e6f-7a8b-9c0d-1e2f3a4b5c6d")
        @get:JsonProperty("id")
        override val id: DrawingId,

        @field:Schema(description = "텍스트가 붙는 대상", example = "BOARD")
        override val scope: DrawingScope,

        @get:Schema(description = "scope=STICKER일 때만 값이 있음", example = "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f")
        @get:JsonProperty("stickerId")
        override val stickerId: StickerId?,

        @field:Schema(description = "글자 색상", example = "#FFFFFF")
        override val color: String,

        @get:Schema(description = "겹침 순서", example = "7")
        @get:JsonProperty("zIndex")
        override val zIndex: Int,

        @field:Schema(description = "표시할 문구. 저장된 줄바꿈이 그대로 내려감", example = "여름 휴가")
        val content: String,

        @field:Schema(description = "글자 크기", example = "26")
        val fontSize: Double,

        @field:Schema(description = "보드 좌표 X. 텍스트 상자의 중심", example = "80")
        val posX: Double,

        @field:Schema(description = "보드 좌표 Y. 텍스트 상자의 중심", example = "290.5")
        val posY: Double,

        @field:Schema(description = "줄바꿈 기준이 된 편집창 폭", example = "280")
        val maxWidth: Double,

        @field:Schema(description = "회전 각도(degree)", example = "0")
        val rotation: Double,
    ) : DrawingV2Response

    companion object {
        fun from(drawing: Drawing): DrawingV2Response =
            when (drawing) {
                is Drawing.Stroke ->
                    Stroke(
                        id = drawing.id,
                        scope = drawing.scope,
                        stickerId = drawing.stickerId,
                        color = drawing.color,
                        zIndex = drawing.zIndex,
                        stroke = drawing.stroke,
                        strokeWidth = drawing.strokeWidth,
                    )

                is Drawing.Text ->
                    Text(
                        id = drawing.id,
                        scope = drawing.scope,
                        stickerId = drawing.stickerId,
                        color = drawing.color,
                        zIndex = drawing.zIndex,
                        content = drawing.content,
                        fontSize = drawing.fontSize,
                        posX = drawing.posX,
                        posY = drawing.posY,
                        maxWidth = drawing.maxWidth,
                        rotation = drawing.rotation,
                    )
            }
    }
}
