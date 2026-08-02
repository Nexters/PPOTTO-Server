package com.github.nexters.ppotto.analysis.presentation.dto

import com.github.nexters.ppotto.analysis.application.AnalysisService
import com.github.nexters.ppotto.analysis.application.PhotoUploadItemRequest
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

@Schema(description = "분석 생성과 사진 업로드 URL 발급 요청")
data class CreateAnalysisRequest(
    @field:NotNull
    @field:Schema(
        description = "결과 스티커가 붙을 보드 ID (uuidv7)",
        example = "01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b",
    )
    val boardId: UUID,
    @field:ArraySchema(
        arraySchema = Schema(description = "촬영 시각 오름차순으로 보내는 사진 90~100장"),
        minItems = AnalysisService.MIN_PHOTO_COUNT,
        maxItems = AnalysisService.MAX_PHOTO_COUNT,
    )
    val photos: List<@Valid PhotoUploadItem>,
)

@Schema(description = "업로드할 사진 정보")
data class PhotoUploadItem(
    @field:NotNull
    @field:Schema(description = "사진 촬영 시각", example = "2026-06-14T13:22:10+09:00")
    val takenAt: Instant,
    @field:NotBlank
    @field:Schema(
        description = "지원 형식. 업로드 시 Content-Type과 일치해야 함",
        example = "image/jpeg",
        allowableValues = ["image/jpeg", "image/png", "image/heic"],
    )
    val contentType: String,
) {
    fun toServiceRequest() = PhotoUploadItemRequest(takenAt, contentType)
}
