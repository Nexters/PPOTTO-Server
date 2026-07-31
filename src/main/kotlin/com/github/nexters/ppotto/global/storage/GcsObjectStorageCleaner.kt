package com.github.nexters.ppotto.global.storage

import com.github.nexters.ppotto.global.config.GcsProperties
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.Storage
import org.springframework.stereotype.Component

@Component
class GcsObjectStorageCleaner(
    private val storage: Storage,
    private val gcsProperties: GcsProperties,
) : ObjectStorageCleaner {
    override fun deleteByPrefix(prefix: String): Int =
        storage
            .list(gcsProperties.bucket, Storage.BlobListOption.prefix(prefix))
            .iterateAll()
            .map { it.name }
            .let(::deleteAll)

    override fun deleteAll(objectKeys: Collection<String>): Int =
        objectKeys
            .toSet()
            .takeIf { it.isNotEmpty() }
            ?.map { BlobId.of(gcsProperties.bucket, it) }
            ?.let { storage.delete(it) }
            ?.count { it }
            ?: 0
}
