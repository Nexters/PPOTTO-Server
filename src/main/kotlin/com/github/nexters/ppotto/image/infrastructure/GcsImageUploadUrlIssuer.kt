package com.github.nexters.ppotto.image.infrastructure

import com.github.nexters.ppotto.global.config.GcsProperties
import com.github.nexters.ppotto.image.domain.ImageUploadUrlIssuer
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.HttpMethod
import com.google.cloud.storage.Storage
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class GcsImageUploadUrlIssuer(
    private val storage: Storage,
    private val gcsProperties: GcsProperties,
) : ImageUploadUrlIssuer {
    override fun issue(
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
                Storage.SignUrlOption.withExtHeaders(mapOf("Content-Type" to contentType)),
            ).toString()
    }
}
