package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.StickerStorage
import com.github.nexters.ppotto.global.config.GcsProperties
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.HttpMethod
import com.google.cloud.storage.Storage
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class GcsStickerStorage(
    private val storage: Storage,
    private val gcsProperties: GcsProperties,
) : StickerStorage {
    override fun upload(
        objectKey: String,
        bytes: ByteArray,
    ): String {
        val blobInfo =
            BlobInfo
                .newBuilder(BlobId.of(gcsProperties.bucket, objectKey))
                .setContentType("image/png")
                .build()
        storage.create(blobInfo, bytes)
        return objectKey
    }

    override fun issueReadUrls(objectKeys: List<String>): List<String> =
        objectKeys.map { objectKey ->
            storage
                .signUrl(
                    BlobInfo.newBuilder(BlobId.of(gcsProperties.bucket, objectKey)).build(),
                    gcsProperties.readSignedUrlExpirationMinutes,
                    TimeUnit.MINUTES,
                    Storage.SignUrlOption.httpMethod(HttpMethod.GET),
                    Storage.SignUrlOption.withV4Signature(),
                ).toString()
        }
}
