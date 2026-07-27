package com.github.nexters.ppotto.image.presentation.dto

import jakarta.validation.constraints.NotNull

data class CompleteImageUploadRequest(
    @field:NotNull
    val result: ImageUploadResult,
)

enum class ImageUploadResult {
    COMPLETED,
    FAILED,
}
