package com.github.nexters.ppotto.analysis.infrastructure

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

    override fun issueUploadUrls(targets: List<PhotoUploadTarget>): List<String> =
        // GCS V4 서명은 로컬 크립토 연산으로, 배치 서명 API가 없다.
        // 반복은 이 구현 내부로 캡슐화하고 서비스 레이어는 이 메서드를 한 번만 호출한다.
        targets.map { signUrl(it.objectKey, it.contentType) }

    override fun existingObjectKeys(prefix: String): Set<String> =
        storage
            .list(gcsProperties.bucket, Storage.BlobListOption.prefix(prefix))
            .iterateAll()
            .map { it.name }
            .toSet()

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
                gcsProperties.signedUrlExpirationMinutes,
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
