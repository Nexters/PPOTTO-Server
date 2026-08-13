package com.github.nexters.ppotto.auth.infrastructure.oauth

import com.github.nexters.ppotto.auth.config.AppleAuthProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.time.Clock
import java.time.Instant
import java.util.Base64

@Component
internal class AppleJwkProvider(
    private val appleOAuthApi: AppleOAuthApi,
    private val properties: AppleAuthProperties,
) {
    private val clock = Clock.systemUTC()
    private val cacheMonitor = Any()
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var cache = JwksCache(Instant.EPOCH, emptyMap())

    fun publicKey(keyId: String): RSAPublicKey? =
        cached(keyId)
            ?: synchronized(cacheMonitor) {
                cached(keyId) ?: refresh()?.keys?.get(keyId)
            }

    private fun cached(keyId: String): RSAPublicKey? =
        cache
            .takeIf { clock.instant().isBefore(it.expiresAt) }
            ?.keys
            ?.get(keyId)

    private fun refresh(): JwksCache? =
        try {
            appleOAuthApi.jwks(properties.jwksUri)
        } catch (e: RestClientException) {
            log
                .warn("애플 JWKS 조회에 실패했습니다.", e)
                .let { null }
        }?.keys
            ?.filter { it.keyType == RSA && it.algorithm == RS256 }
            ?.associate { it.keyId to it.toPublicKey() }
            ?.let { JwksCache(clock.instant().plusSeconds(properties.jwksCacheSeconds), it) }
            ?.also { cache = it }

    private fun AppleJwk.toPublicKey(): RSAPublicKey =
        Base64
            .getUrlDecoder()
            .let {
                RSAPublicKeySpec(
                    BigInteger(1, it.decode(modulus)),
                    BigInteger(1, it.decode(exponent)),
                )
            }.let { KeyFactory.getInstance(RSA).generatePublic(it) as RSAPublicKey }

    private data class JwksCache(
        val expiresAt: Instant,
        val keys: Map<String, RSAPublicKey>,
    )

    private companion object {
        const val RSA = "RSA"
        const val RS256 = "RS256"
    }
}
