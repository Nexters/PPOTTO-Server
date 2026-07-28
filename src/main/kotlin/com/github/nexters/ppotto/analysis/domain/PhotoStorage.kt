package com.github.nexters.ppotto.analysis.domain

interface PhotoStorage {
    fun issueUploadUrls(targets: List<PhotoUploadTarget>): List<String>

    fun existingObjectKeys(prefix: String): Set<String>
}

data class PhotoUploadTarget(
    val objectKey: String,
    val contentType: String,
)
