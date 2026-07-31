package com.github.nexters.ppotto.analysis.domain

import java.time.Instant

interface PhotoStorage {
    fun issueUploadUrls(targets: List<PhotoUploadTarget>): List<String>

    fun issueReadUrls(objectKeys: Collection<String>): Map<String, String>

    fun existingObjects(prefix: String): Map<String, BlobMeta>

    fun deleteByPrefix(prefix: String): Int
}

data class PhotoUploadTarget(
    val objectKey: String,
    val contentType: String,
)

data class BlobMeta(
    val size: Long,
    val createdAt: Instant,
)
