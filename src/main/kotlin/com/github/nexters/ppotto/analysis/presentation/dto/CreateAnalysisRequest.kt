package com.github.nexters.ppotto.analysis.presentation.dto

import com.github.nexters.ppotto.analysis.application.PhotoUploadItemRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class CreateAnalysisRequest(
    @field:NotNull
    val boardId: UUID,
    val photos: List<@Valid PhotoUploadItem>,
)

data class PhotoUploadItem(
    @field:NotNull
    val takenAt: Instant,
    @field:NotBlank
    val contentType: String,
) {
    fun toServiceRequest() = PhotoUploadItemRequest(takenAt, contentType)
}
