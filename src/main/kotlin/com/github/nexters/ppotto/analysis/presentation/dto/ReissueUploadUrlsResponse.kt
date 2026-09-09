package com.github.nexters.ppotto.analysis.presentation.dto

import com.github.nexters.ppotto.analysis.application.PhotoUploadUrlItem
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "재발급된 사진별 업로드 URL")
data class ReissueUploadUrlsResponse(
    @field:Schema(description = "재발급 대상(PENDING) 사진의 업로드 URL 목록")
    val uploads: List<PhotoUploadUrlResponse>,
) {
    companion object {
        fun from(uploads: List<PhotoUploadUrlItem>): ReissueUploadUrlsResponse =
            ReissueUploadUrlsResponse(uploads.map(PhotoUploadUrlResponse::from))
    }
}
