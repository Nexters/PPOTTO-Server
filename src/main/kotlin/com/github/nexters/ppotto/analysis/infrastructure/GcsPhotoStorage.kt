package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.BlobMeta
import com.github.nexters.ppotto.analysis.domain.Photo
import com.github.nexters.ppotto.analysis.domain.PhotoStorage
import com.github.nexters.ppotto.global.config.GcsProperties
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.global.storage.GcsReadUrlIssuer
import com.github.nexters.ppotto.global.storage.ObjectStorageCleaner
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
    private val gcsReadUrlIssuer: GcsReadUrlIssuer,
    private val objectStorageCleaner: ObjectStorageCleaner,
) : PhotoStorage {
    override fun issueUploadUrls(photos: List<Photo>): Map<PhotoId, String> =
        photos.associate { it.id to signUploadUrl(PhotoObjectKeys.keyFor(it), it.contentType.mimeType) }

    override fun issueReadUrls(photos: List<Photo>): Map<PhotoId, String> {
        val objectKeyByPhotoId = photos.associate { it.id to PhotoObjectKeys.keyFor(it) }
        val readUrls = gcsReadUrlIssuer.issue(objectKeyByPhotoId.values)
        return objectKeyByPhotoId.mapValues { (_, objectKey) ->
            readUrls[objectKey] ?: error("사진 읽기 URL이 누락되었습니다: $objectKey")
        }
    }

    override fun sourceUri(photo: Photo): String = "gs://${gcsProperties.bucket}/${PhotoObjectKeys.keyFor(photo)}"

    override fun uploadedObjects(
        analysisId: AnalysisId,
        photos: List<Photo>,
    ): Map<PhotoId, BlobMeta> {
        if (photos.isEmpty()) return emptyMap()

        val blobMetaByObjectKey =
            storage
                .list(gcsProperties.bucket, Storage.BlobListOption.prefix(PhotoObjectKeys.prefixFor(analysisId)))
                .iterateAll()
                .associate { it.name to BlobMeta(it.size, it.createTimeOffsetDateTime.toInstant()) }
        return photos.mapNotNull { photo -> blobMetaByObjectKey[PhotoObjectKeys.keyFor(photo)]?.let { photo.id to it } }.toMap()
    }

    override fun deleteAll(analysisId: AnalysisId): Int = objectStorageCleaner.deleteByPrefix(PhotoObjectKeys.prefixFor(analysisId))

    private fun signUploadUrl(
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

    companion object {
        private const val MAX_PHOTO_SIZE_BYTES = 15_728_640
    }
}
