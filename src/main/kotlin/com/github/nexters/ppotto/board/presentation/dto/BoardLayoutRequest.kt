package com.github.nexters.ppotto.board.presentation.dto

import com.github.nexters.ppotto.board.application.BoardLayoutService
import com.github.nexters.ppotto.board.application.BoardLayoutUpdateCommand
import com.github.nexters.ppotto.board.application.DrawingCreateCommand
import com.github.nexters.ppotto.board.application.port.BoardStickerLayoutCommand
import com.github.nexters.ppotto.board.domain.DrawingScope
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.util.UUID

@Schema(description = "보드 편집 결과 일괄 저장 요청")
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
    val id: UUID,
    @field:Size(min = 1, max = BoardLayoutService.MAX_STICKER_TITLE_LENGTH)
    val title: String? = null,
    val posX: Double,
    val posY: Double,
    val scale: Double,
    val rotation: Double,
    val zIndex: Int,
    val badgeOffsetX: Double,
    val badgeOffsetY: Double,
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
    val created: List<@Valid DrawingCreateRequest>? = null,
    val deletedIds: List<UUID>? = null,
)

@Schema(description = "새 그림")
data class DrawingCreateRequest(
    val id: UUID,
    val scope: DrawingScope,
    val stickerId: UUID? = null,
    @field:NotEmpty
    val stroke: Map<String, Any?>,
    @field:NotBlank
    val color: String,
    @field:Positive
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
