package com.github.nexters.ppotto.analysis.domain

import com.github.nexters.ppotto.global.identifier.PhotoId
import java.util.UUID

data class PhotoRef(
    val photoId: PhotoId,
    val sourceUri: String,
    val mimeType: String,
    val burstGroupId: UUID? = null,
    val isRepresentative: Boolean = true,
)

fun Photo.toRef(sourceUri: String): PhotoRef =
    PhotoRef(
        photoId = id,
        sourceUri = sourceUri,
        mimeType = contentType.mimeType,
        burstGroupId = burstGroupId,
        isRepresentative = isRepresentative,
    )
