package com.github.nexters.ppotto.analysis.presentation.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import com.github.nexters.ppotto.analysis.application.PhotoUploadGroupRequest
import com.github.nexters.ppotto.analysis.application.PhotoUploadItemRequest
import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
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
        arraySchema =
            Schema(
                description = "촬영 시각 오름차순으로 보내는 사진 그룹. 그룹은 20~100개, 그룹당 사진은 1~10장이어야 한다.",
            ),
    )
    val photos: List<@Valid PhotoUploadGroup>,
) {
    fun toServiceRequests(): List<PhotoUploadGroupRequest> = photos.map { it.toServiceRequest() }
}

@Schema(description = "사진 그룹. 연사가 아니면 원소 1개, 연사면 여러 장(최대 10장)")
data class PhotoUploadGroup(
    @field:Size(min = 1)
    @field:ArraySchema(arraySchema = Schema(description = "그룹에 속한 사진들. 촬영 시각 오름차순"))
    val items: List<@Valid PhotoUploadItem>,
) {
    fun toServiceRequest(): PhotoUploadGroupRequest =
        PhotoUploadGroupRequest(
            items.map {
                PhotoUploadItemRequest(
                    takenAt = it.takenAt,
                    contentType = it.contentType.toDomain(),
                    isRepresentative = it.isRepresentative,
                )
            },
        )
}

@Schema(description = "업로드할 사진 정보")
data class PhotoUploadItem(
    @field:NotNull
    @field:Schema(description = "사진 촬영 시각", example = "2026-06-14T13:22:10+09:00")
    val takenAt: Instant,

    @field:NotNull
    @field:Schema(description = "지원 형식. 업로드 시 Content-Type과 일치해야 함", example = "image/jpeg")
    val contentType: PhotoUploadContentType,

    @field:NotNull
    @get:JsonProperty("isRepresentative")
    @get:Schema(description = "연사 그룹 내 대표 사진 여부")
    val isRepresentative: Boolean,
)

enum class PhotoUploadContentType(
    @get:JsonValue val mimeType: String,
    private val domainType: PhotoContentType,
) {
    JPEG("image/jpeg", PhotoContentType.JPEG),
    PNG("image/png", PhotoContentType.PNG),
    WEBP("image/webp", PhotoContentType.WEBP),
    ;

    fun toDomain(): PhotoContentType = domainType
}
