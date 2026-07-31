package com.github.nexters.ppotto.global.storage

import com.github.nexters.ppotto.global.config.GcsProperties
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.HttpMethod
import com.google.cloud.storage.Storage
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class GcsReadUrlIssuer(
    private val storage: Storage,
    private val gcsProperties: GcsProperties,
) {
    fun issue(objectKeys: Collection<String>): Map<String, String> = objectKeys.toSet().associateWith(::sign)

    private fun sign(objectKey: String): String =
        BlobInfo
            .newBuilder(BlobId.of(gcsProperties.bucket, objectKey))
            .build()
            .let {
                storage.signUrl(
                    it,
                    gcsProperties.readSignedUrlExpirationMinutes,
                    TimeUnit.MINUTES,
                    Storage.SignUrlOption.httpMethod(HttpMethod.GET),
                    Storage.SignUrlOption.withV4Signature(),
                )
            }.toString()
}
