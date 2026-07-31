package com.github.nexters.ppotto.analysis.support

import com.github.nexters.ppotto.analysis.domain.BlobMeta
import com.github.nexters.ppotto.analysis.domain.PhotoStorage
import com.github.nexters.ppotto.analysis.domain.PhotoUploadTarget
import java.time.Instant

class FakePhotoStorage : PhotoStorage {
    private val objects = mutableMapOf<String, BlobMeta>()

    override fun issueUploadUrls(targets: List<PhotoUploadTarget>): List<String> =
        targets.map {
            objects[it.objectKey] = BlobMeta(size = 1, createdAt = Instant.now())
            "https://fake-signed-url/${it.objectKey}"
        }

    override fun issueReadUrls(objectKeys: List<String>): List<String> = objectKeys.map { "https://fake-read-url/$it" }

    override fun existingObjects(prefix: String): Map<String, BlobMeta> = objects.filterKeys { it.startsWith(prefix) }

    fun markMissing(objectKey: String) {
        objects -= objectKey
    }

    fun markUploaded(
        objectKey: String,
        size: Long = 1,
        createdAt: Instant = Instant.now(),
    ) {
        objects[objectKey] = BlobMeta(size, createdAt)
    }

    fun reset() {
        objects.clear()
    }

    fun clear() {
        objects.clear()
    }
}
