package com.github.nexters.ppotto.image.presentation

import com.github.nexters.ppotto.global.response.ApiResponse
import com.github.nexters.ppotto.image.application.ImageUploadService
import com.github.nexters.ppotto.image.domain.UploadStatus
import com.github.nexters.ppotto.image.presentation.dto.CompleteImageUploadRequest
import com.github.nexters.ppotto.image.presentation.dto.CompleteImageUploadResponse
import com.github.nexters.ppotto.image.presentation.dto.ImageUploadResult
import com.github.nexters.ppotto.image.presentation.dto.IssueImageUploadUrlsRequest
import com.github.nexters.ppotto.image.presentation.dto.IssueImageUploadUrlsResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/boards/{boardId}/images")
class ImageUploadController(
    private val imageUploadService: ImageUploadService,
) {
    @PostMapping("/signed-urls")
    fun issue(
        @PathVariable boardId: UUID,
        @Valid @RequestBody request: IssueImageUploadUrlsRequest,
    ): ApiResponse<IssueImageUploadUrlsResponse> {
        val result = imageUploadService.issueUploadUrls(boardId, request.fileNames)
        return ApiResponse.success(IssueImageUploadUrlsResponse.from(result))
    }

    @PatchMapping("/{imageId}/upload-status")
    fun complete(
        @PathVariable boardId: UUID,
        @PathVariable imageId: UUID,
        @Valid @RequestBody request: CompleteImageUploadRequest,
    ): ApiResponse<CompleteImageUploadResponse> {
        val newStatus =
            when (request.result) {
                ImageUploadResult.COMPLETED -> UploadStatus.COMPLETED
                ImageUploadResult.FAILED -> UploadStatus.FAILED
            }
        val image = imageUploadService.completeUpload(boardId, imageId, newStatus)
        return ApiResponse.success(CompleteImageUploadResponse.from(image))
    }
}
