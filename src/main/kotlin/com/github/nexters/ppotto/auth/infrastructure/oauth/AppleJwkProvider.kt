package com.github.nexters.ppotto.auth.infrastructure.oauth

import com.github.nexters.ppotto.auth.config.AppleAuthProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.time.Instant
import java.util.Base64

@Component
internal class AppleJwkProvider(
    private val appleOAuthApi: AppleOAuthApi,
    private val properties: AppleAuthProperties,
) {
    private val cacheMonitor = Any()
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var cache = JwksCache(Instant.EPOCH, emptyMap())

    fun publicKey(keyId: String): RSAPublicKey? =
        cached(keyId)
            ?: synchronized(cacheMonitor) {
                cached(keyId) ?: refresh()?.keys?.get(keyId)
            }

    private fun cached(keyId: String): RSAPublicKey? {
        val current = cache
        if (!Instant.now().isBefore(current.expiresAt)) return null
        return current.keys[keyId]
    }

    private fun refresh(): JwksCache? {
        val response =
            try {
                appleOAuthApi.jwks(properties.jwksUri)
            } catch (e: RestClientException) {
                log.warn("애플 JWKS 조회에 실패했습니다.", e)
                null
            } ?: return null

        val keys =
            response.keys
                .filter { it.keyType == RSA && it.algorithm == RS256 }
                .associate { it.keyId to it.toPublicKey() }
        val refreshed = JwksCache(Instant.now().plusSeconds(properties.jwksCacheSeconds), keys)
        cache = refreshed
        return refreshed
    }

    private fun AppleJwk.toPublicKey(): RSAPublicKey {
        val decoder = Base64.getUrlDecoder()
        val spec =
            RSAPublicKeySpec(
                BigInteger(1, decoder.decode(modulus)),
                BigInteger(1, decoder.decode(exponent)),
            )
        return KeyFactory.getInstance(RSA).generatePublic(spec) as RSAPublicKey
    }

    private data class JwksCache(
        val expiresAt: Instant,
        val keys: Map<String, RSAPublicKey>,
    )

    private companion object {
        const val RSA = "RSA"
        const val RS256 = "RS256"
    }
}
