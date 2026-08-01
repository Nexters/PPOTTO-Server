package com.github.nexters.ppotto.sticker.presentation.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.sticker.application.StickerTitleResult
import com.github.nexters.ppotto.sticker.domain.Sticker
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "스티커 제목 변경 요청")
data class UpdateStickerTitleRequest(
    @field:NotBlank
    @field:Size(max = Sticker.MAX_TITLE_LENGTH)
    @field:Schema(description = "새 스티커 제목. 최대 15자", example = "고양이 모음집")
    val title: String,
)

@Schema(description = "변경된 스티커 제목")
data class UpdateStickerTitleResponse(
    @get:Schema(description = "스티커 ID (uuidv7)", example = "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f")
    @get:JsonProperty("id")
    val id: StickerId,
    @field:Schema(description = "변경된 제목", example = "고양이 모음집")
    val title: String,
) {
    companion object {
        fun from(result: StickerTitleResult) = UpdateStickerTitleResponse(result.id, result.title)
    }
}
