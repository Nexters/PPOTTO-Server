package com.github.nexters.ppotto.analysis.presentation.dto

import com.github.nexters.ppotto.analysis.application.PhotoUploadItemRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateAnalysisRequest(
    @field:NotNull
    val boardId: UUID,
    @field:NotEmpty
    @field:Size(min = 90, max = 100)
    val photos: List<@Valid PhotoUploadItem>,
)

data class PhotoUploadItem(
    @field:NotNull
    val takenAt: Instant,
    val contentType: String? = null,
) {
    fun toServiceRequest() = PhotoUploadItemRequest(takenAt, contentType)
}
