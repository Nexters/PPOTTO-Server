package com.github.nexters.ppotto.image.presentation.dto

import com.github.nexters.ppotto.image.application.ImageUploadUrlsResult
import java.util.UUID

data class IssueImageUploadUrlsResponse(
    val uploadSessionId: UUID,
    val images: List<ImageUploadUrlItem>,
) {
    companion object {
        fun from(result: ImageUploadUrlsResult): IssueImageUploadUrlsResponse =
            IssueImageUploadUrlsResponse(
                uploadSessionId = result.uploadSessionId,
                images =
                    result.items.map {
                        ImageUploadUrlItem(imageId = it.imageId, fileName = it.fileName, uploadUrl = it.uploadUrl)
                    },
            )
    }
}

data class ImageUploadUrlItem(
    val imageId: UUID,
    val fileName: String,
    val uploadUrl: String,
)
