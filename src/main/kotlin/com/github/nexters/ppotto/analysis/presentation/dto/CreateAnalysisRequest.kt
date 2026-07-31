package com.github.nexters.ppotto.analysis.presentation.dto

import com.github.nexters.ppotto.analysis.application.PhotoUploadItemRequest
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

@Schema(description = "분석 생성과 사진 업로드 URL 발급 요청")
data class CreateAnalysisRequest(
    @field:NotNull
    @field:Schema(description = "분석 결과를 배치할 보드 ID")
    val boardId: UUID,
    @field:Schema(description = "촬영 시각과 형식을 담은 사진 90~100장")
    val photos: List<@Valid PhotoUploadItem>,
)

@Schema(description = "업로드할 사진 정보")
data class PhotoUploadItem(
    @field:NotNull
    @field:Schema(description = "사진 촬영 시각", example = "2026-07-01T00:00:00Z")
    val takenAt: Instant,
    @field:NotBlank
    @field:Schema(description = "지원 형식", example = "image/jpeg")
    val contentType: String,
) {
    fun toServiceRequest() = PhotoUploadItemRequest(takenAt, contentType)
}
