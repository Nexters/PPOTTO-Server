package com.github.nexters.ppotto.sticker.presentation.dto

import com.github.nexters.ppotto.sticker.domain.RecapCommentPosition
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import java.util.UUID

@Schema(description = "리캡 코멘트 위치 일괄 저장 요청. 바뀐 말풍선 코멘트만 보냄")
data class UpdateRecapCommentPositionsRequest(
    @field:Valid
    @field:NotEmpty
    @field:Schema(description = "변경된 말풍선 코멘트 위치")
    val comments: List<RecapCommentPositionRequest>,
)

@Schema(description = "말풍선 코멘트 위치")
data class RecapCommentPositionRequest(
    @field:Schema(description = "코멘트 ID (uuidv7)", example = "01983f2d-1a2b-7c3d-8e4f-5a6b7c8d9e0f")
    val id: UUID,

    @field:Schema(description = "스티커 기준 상대 좌표 X", example = "-96")
    val posX: Double,

    @field:Schema(description = "스티커 기준 상대 좌표 Y", example = "-150")
    val posY: Double,
) {
    fun toDomain(): RecapCommentPosition =
        RecapCommentPosition(
            id = id,
            posX = posX,
            posY = posY,
        )
}
