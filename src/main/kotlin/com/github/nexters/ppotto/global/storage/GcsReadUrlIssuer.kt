package com.github.nexters.ppotto.global.storage

import com.github.nexters.ppotto.global.config.GcsProperties
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.HttpMethod
import com.google.cloud.storage.Storage
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.TimeUnit

@Component
class GcsReadUrlIssuer(
    private val storage: Storage,
    private val gcsProperties: GcsProperties,
    private val redisTemplate: StringRedisTemplate,
) {
    private val cacheExpiration =
        Duration
            .ofMinutes(gcsProperties.readSignedUrlExpirationMinutes)
            .let { it.minus(minOf(it.dividedBy(10), CACHE_EXPIRATION_MARGIN)) }

    fun issue(objectKeys: Collection<String>): Map<String, String> =
        objectKeys
            .toSet()
            .takeIf(Set<String>::isNotEmpty)
            ?.let { keys ->
                try {
                    issueCached(keys)
                } catch (_: DataAccessException) {
                    keys.associateWith(::sign)
                }
            }.orEmpty()

    private fun issueCached(objectKeys: Set<String>): Map<String, String> {
        val cacheKeys = objectKeys.associateWith(::cacheKey)
        val cachedUrls =
            cacheKeys.values
                .zip(
                    redisTemplate
                        .opsForValue()
                        .multiGet(cacheKeys.values)
                        .orEmpty(),
                ).toMap()
        return objectKeys.associateWith { objectKey ->
            val cacheKey = cacheKeys.getValue(objectKey)
            cachedUrls[cacheKey]
                ?: sign(objectKey).also { redisTemplate.opsForValue().set(cacheKey, it, cacheExpiration) }
        }
    }

    private fun cacheKey(objectKey: String): String = "$CACHE_KEY_PREFIX${gcsProperties.bucket}:$objectKey"

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

    private companion object {
        const val CACHE_KEY_PREFIX = "gcs:read-url:"
        val CACHE_EXPIRATION_MARGIN: Duration = Duration.ofMinutes(5)
    }
}
