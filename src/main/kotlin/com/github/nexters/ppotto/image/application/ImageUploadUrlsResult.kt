package com.github.nexters.ppotto.image.application

import java.util.UUID

data class ImageUploadUrlsResult(
    val uploadSessionId: UUID,
    val items: List<ImageUploadUrlItemResult>,
)

data class ImageUploadUrlItemResult(
    val imageId: UUID,
    val fileName: String,
    val uploadUrl: String,
)
