package com.github.nexters.ppotto.analysis.support

import com.github.nexters.ppotto.analysis.domain.BlobMeta
import com.github.nexters.ppotto.analysis.domain.PhotoStorage
import com.github.nexters.ppotto.analysis.domain.PhotoUploadTarget
import java.time.Instant

class FakePhotoStorage : PhotoStorage {
    private val objects = mutableMapOf<String, BlobMeta>()
    val deletedPrefixes = mutableListOf<String>()

    override fun issueUploadUrls(targets: List<PhotoUploadTarget>): List<String> =
        targets.map {
            objects[it.objectKey] = BlobMeta(size = 1, createdAt = Instant.now())
            "https://fake-signed-url/${it.objectKey}"
        }

    override fun issueReadUrls(objectKeys: Collection<String>): Map<String, String> =
        objectKeys.toSet().associateWith { "https://fake-read-url/$it" }

    override fun existingObjects(prefix: String): Map<String, BlobMeta> = objects.filterKeys { it.startsWith(prefix) }

    override fun deleteByPrefix(prefix: String): Int =
        prefix
            .also(deletedPrefixes::add)
            .let { objects.keys.filter { key -> key.startsWith(it) } }
            .onEach { objects -= it }
            .size

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
        deletedPrefixes.clear()
    }

    fun clear() {
        objects.clear()
        deletedPrefixes.clear()
    }
}
