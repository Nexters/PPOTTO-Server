package com.github.nexters.ppotto.analysis.infrastructure.storage

import com.github.nexters.ppotto.analysis.application.port.StickerStorage
import com.github.nexters.ppotto.global.config.GcsProperties
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.storage.ObjectStorageCleaner
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import org.springframework.stereotype.Component

@Component
class GcsStickerStorage(
    private val storage: Storage,
    private val gcsProperties: GcsProperties,
    private val objectStorageCleaner: ObjectStorageCleaner,
) : StickerStorage {
    override fun upload(
        objectKey: String,
        bytes: ByteArray,
    ) {
        val blobInfo =
            BlobInfo
                .newBuilder(BlobId.of(gcsProperties.bucket, objectKey))
                .setContentType("image/png")
                .build()
        storage.create(blobInfo, bytes)
    }

    override fun deleteAll(analysisId: AnalysisId): Int = objectStorageCleaner.deleteByPrefix(StickerObjectKeys.prefixFor(analysisId))
}
