package com.github.nexters.ppotto.board.presentation.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.github.nexters.ppotto.board.application.model.BoardLayoutUpdateCommand
import com.github.nexters.ppotto.board.application.model.DrawingLayoutDeleteCommand
import com.github.nexters.ppotto.board.application.model.DrawingLayoutUpdateCommand
import com.github.nexters.ppotto.board.application.port.BoardStickerLayoutCommand
import com.github.nexters.ppotto.board.domain.Drawing
import com.github.nexters.ppotto.board.domain.DrawingScope
import com.github.nexters.ppotto.board.domain.NewDrawing
import com.github.nexters.ppotto.global.error.CommonErrorCode
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.DrawingId
import com.github.nexters.ppotto.global.identifier.StickerId
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

const val DRAWING_COLOR_PATTERN = "^#[0-9A-Fa-f]{6}$"

@Schema(description = "보드 편집 결과 일괄 저장 요청 (v2). 변경된 항목만 보냄")
data class BoardLayoutV2Request(
    @field:Valid
    @field:Schema(description = "변경된 스티커 배치")
    val stickers: List<StickerLayoutV2Request>? = null,

    @field:Valid
    @field:Schema(description = "선과 텍스트의 생성·수정·삭제 변경분")
    val drawings: DrawingChangesV2Request? = null,
) {
    fun toCommand(boardId: BoardId): BoardLayoutUpdateCommand =
        BoardLayoutUpdateCommand(
            stickers = stickers.orEmpty().map(StickerLayoutV2Request::toCommand),
            createdDrawings = emptyList(),
            deletedDrawingIds = emptyList(),
            insertedDrawings =
                drawings
                    ?.created
                    .orEmpty()
                    .map { it.toDomain(boardId) },
            updatedDrawings =
                drawings
                    ?.updated
                    .orEmpty()
                    .map { it.toCommand(boardId) },
            versionedDeletedDrawings =
                drawings
                    ?.deleted
                    .orEmpty()
                    .map(DrawingDeleteV2Request::toCommand),
        )
}

@Schema(description = "스티커 배치 (v2)")
data class StickerLayoutV2Request(
    @get:Schema(description = "스티커 ID (uuidv7)", example = "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f")
    @get:JsonProperty("id")
    val id: StickerId,

    @field:Schema(description = "보드 좌표 X", example = "80")
    val posX: Double,

    @field:Schema(description = "보드 좌표 Y", example = "290.5")
    val posY: Double,

    @field:Schema(description = "확대 비율", example = "1.1")
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

    @field:PositiveOrZero
    @field:Schema(description = "조회 당시의 스티커 배치 버전", example = "0")
    val expectedLayoutVersion: Long,
) {
    fun toCommand(): BoardStickerLayoutCommand =
        BoardStickerLayoutCommand(
            id = id,
            title = null,
            posX = posX,
            posY = posY,
            scale = scale,
            rotation = rotation,
            zIndex = zIndex,
            badgeOffsetX = badgeOffsetX,
            badgeOffsetY = badgeOffsetY,
            badgeRotation = badgeRotation,
            expectedLayoutVersion = expectedLayoutVersion,
        )
}

@Schema(description = "선과 텍스트의 생성·수정·삭제 변경분")
data class DrawingChangesV2Request(
    @field:Valid
    @field:Schema(description = "새로 만든 선과 텍스트. 이미 존재하는 ID는 생성할 수 없음")
    val created: List<DrawingCreateV2Request>? = null,

    @field:Valid
    @field:Schema(description = "기존 선과 텍스트의 수정. 조회 당시 배치 버전이 필요함")
    val updated: List<DrawingUpdateV2Request>? = null,

    @field:Valid
    @field:Schema(description = "삭제할 선과 텍스트의 ID와 조회 당시 배치 버전")
    val deleted: List<DrawingDeleteV2Request>? = null,

    @get:JsonProperty("deletedIds")
    @field:Schema(hidden = true)
    val legacyDeletedIds: List<DrawingId>? = null,
) {
    init {
        if (legacyDeletedIds != null) throw InvalidInputException(CommonErrorCode.INVALID_INPUT)
    }
}

data class DrawingDeleteV2Request(
    val id: DrawingId,

    @field:PositiveOrZero
    val expectedLayoutVersion: Long,
) {
    fun toCommand() = DrawingLayoutDeleteCommand(id, expectedLayoutVersion)
}

@Schema(
    description = "기존 선 또는 텍스트의 수정. type이 판별자다",
    discriminatorProperty = "type",
    oneOf = [DrawingUpdateV2Request.Stroke::class, DrawingUpdateV2Request.Text::class],
)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = DrawingUpdateV2Request.Stroke::class, name = "STROKE"),
    JsonSubTypes.Type(value = DrawingUpdateV2Request.Text::class, name = "TEXT"),
)
sealed interface DrawingUpdateV2Request {
    fun toCommand(boardId: BoardId): DrawingLayoutUpdateCommand

    @Schema(name = "DrawingUpdateStrokeRequest", description = "기존 선 수정")
    data class Stroke(
        val id: DrawingId,
        val scope: DrawingScope,
        val stickerId: StickerId? = null,

        @field:NotBlank
        @field:Pattern(regexp = DRAWING_COLOR_PATTERN)
        val color: String,

        val zIndex: Int,

        @field:NotEmpty
        val stroke: Map<String, Any?>,

        @field:Positive
        val strokeWidth: Double,

        @field:PositiveOrZero
        val expectedLayoutVersion: Long,
    ) : DrawingUpdateV2Request {
        override fun toCommand(boardId: BoardId) =
            DrawingLayoutUpdateCommand(
                NewDrawing.Stroke(id, boardId, stickerId, scope, color, zIndex, stroke, strokeWidth),
                expectedLayoutVersion,
            )
    }

    @Schema(name = "DrawingUpdateTextRequest", description = "기존 텍스트 수정")
    data class Text(
        val id: DrawingId,
        val scope: DrawingScope,
        val stickerId: StickerId? = null,

        @field:NotBlank
        @field:Pattern(regexp = DRAWING_COLOR_PATTERN)
        val color: String,

        val zIndex: Int,

        @field:NotBlank
        @field:Size(max = Drawing.Text.MAX_CONTENT_LENGTH)
        val content: String,

        @field:Positive
        val fontSize: Double,
        val posX: Double,
        val posY: Double,

        @field:Positive
        val maxWidth: Double,
        val rotation: Double = 0.0,

        @field:PositiveOrZero
        val expectedLayoutVersion: Long,
    ) : DrawingUpdateV2Request {
        override fun toCommand(boardId: BoardId) =
            DrawingLayoutUpdateCommand(
                NewDrawing.Text(id, boardId, stickerId, scope, color, zIndex, content, fontSize, posX, posY, maxWidth, rotation),
                expectedLayoutVersion,
            )
    }
}

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

    fun toDomain(boardId: BoardId): NewDrawing

    @Schema(name = "DrawingCreateStrokeRequest", description = "새 선")
    data class Stroke(
        @get:Schema(
            description = "클라이언트가 생성한 새 선의 uuidv7",
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
        override fun toDomain(boardId: BoardId): NewDrawing =
            NewDrawing.Stroke(
                id = id,
                boardId = boardId,
                stickerId = stickerId,
                scope = scope,
                color = color,
                zIndex = zIndex,
                stroke = stroke,
                strokeWidth = strokeWidth,
            )
    }

    @Schema(name = "DrawingCreateTextRequest", description = "새 텍스트")
    data class Text(
        @get:Schema(
            description = "클라이언트가 생성한 새 텍스트의 uuidv7",
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
        override fun toDomain(boardId: BoardId): NewDrawing =
            NewDrawing.Text(
                id = id,
                boardId = boardId,
                stickerId = stickerId,
                scope = scope,
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
