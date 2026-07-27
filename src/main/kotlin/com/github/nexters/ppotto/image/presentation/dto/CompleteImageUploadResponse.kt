package com.github.nexters.ppotto.image.presentation.dto

import com.github.nexters.ppotto.image.domain.Image
import com.github.nexters.ppotto.image.domain.UploadStatus
import java.util.UUID

data class CompleteImageUploadResponse(
    val imageId: UUID,
    val uploadStatus: UploadStatus,
) {
    companion object {
        fun from(image: Image): CompleteImageUploadResponse = CompleteImageUploadResponse(image.id, image.uploadStatus)
    }
}
