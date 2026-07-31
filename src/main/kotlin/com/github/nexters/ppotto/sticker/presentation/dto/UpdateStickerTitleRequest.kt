package com.github.nexters.ppotto.sticker.presentation.dto

import com.github.nexters.ppotto.sticker.application.StickerTitleResult
import com.github.nexters.ppotto.sticker.domain.Sticker
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

@Schema(description = "스티커 제목 변경 요청")
data class UpdateStickerTitleRequest(
    @field:NotBlank
    @field:Size(max = Sticker.MAX_TITLE_LENGTH)
    @field:Schema(description = "새 스티커 제목", example = "제주 여행")
    val title: String,
)

@Schema(description = "변경된 스티커 제목")
data class UpdateStickerTitleResponse(
    val id: UUID,
    val title: String,
) {
    companion object {
        fun from(result: StickerTitleResult) = UpdateStickerTitleResponse(result.id, result.title)
    }
}
