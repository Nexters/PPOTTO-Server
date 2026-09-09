package com.github.nexters.ppotto.analysis.domain

import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.PhotoId
import java.time.Instant

interface PhotoStorage {
    fun issueUploadUrls(photos: List<Photo>): Map<PhotoId, String>

    fun issueReadUrls(photos: List<Photo>): Map<PhotoId, String>

    fun sourceUri(photo: Photo): String

    fun uploadedObjects(
        analysisId: AnalysisId,
        photos: List<Photo>,
    ): Map<PhotoId, BlobMeta>

    fun deleteAll(analysisId: AnalysisId): Int
}

data class BlobMeta(
    val size: Long,
    val createdAt: Instant,
)
