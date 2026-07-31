package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.BlobMeta
import com.github.nexters.ppotto.analysis.domain.PhotoStorage
import com.github.nexters.ppotto.analysis.domain.PhotoUploadTarget
import com.github.nexters.ppotto.global.config.GcsProperties
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.HttpMethod
import com.google.cloud.storage.Storage
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class GcsPhotoStorage(
    private val storage: Storage,
    private val gcsProperties: GcsProperties,
) : PhotoStorage {
    companion object {
        private const val MAX_PHOTO_SIZE_BYTES = 15_728_640
    }

    override fun issueUploadUrls(targets: List<PhotoUploadTarget>): List<String> = targets.map { signUrl(it.objectKey, it.contentType) }

    override fun existingObjects(prefix: String): Map<String, BlobMeta> =
        storage
            .list(gcsProperties.bucket, Storage.BlobListOption.prefix(prefix))
            .iterateAll()
            .associate { it.name to BlobMeta(it.size, it.createTimeOffsetDateTime.toInstant()) }

    private fun signUrl(
        objectKey: String,
        contentType: String,
    ): String {
        val blobInfo =
            BlobInfo
                .newBuilder(BlobId.of(gcsProperties.bucket, objectKey))
                .setContentType(contentType)
                .build()

        return storage
            .signUrl(
                blobInfo,
                gcsProperties.uploadSignedUrlExpirationMinutes,
                TimeUnit.MINUTES,
                Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                Storage.SignUrlOption.withV4Signature(),
                Storage.SignUrlOption.withExtHeaders(
                    mapOf(
                        "Content-Type" to contentType,
                        "x-goog-content-length-range" to "0,$MAX_PHOTO_SIZE_BYTES",
                    ),
                ),
            ).toString()
    }
}
