package com.github.nexters.ppotto.sticker.presentation.dto

import com.github.nexters.ppotto.sticker.application.StickerTitleResult
import com.github.nexters.ppotto.sticker.domain.Sticker
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class UpdateStickerTitleRequest(
    @field:NotBlank
    @field:Size(max = Sticker.MAX_TITLE_LENGTH)
    val title: String,
)

data class UpdateStickerTitleResponse(
    val id: UUID,
    val title: String,
) {
    companion object {
        fun from(result: StickerTitleResult) = UpdateStickerTitleResponse(result.id, result.title)
    }
}
