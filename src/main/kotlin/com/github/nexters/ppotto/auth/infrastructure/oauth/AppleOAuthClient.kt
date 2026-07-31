package com.github.nexters.ppotto.auth.infrastructure.oauth

import com.github.nexters.ppotto.auth.application.port.OAuthClient
import com.github.nexters.ppotto.auth.config.AppleAuthProperties
import com.github.nexters.ppotto.auth.domain.AuthErrorCode
import com.github.nexters.ppotto.auth.domain.LoginCommand
import com.github.nexters.ppotto.auth.domain.OAuthProvider
import com.github.nexters.ppotto.auth.domain.SocialProfile
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.error.UnauthorizedException
import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.text.ParseException
import java.time.Clock
import java.time.Instant
import java.util.Base64

@Component
class AppleOAuthClient(
    private val httpService: AppleOAuthHttpService,
    private val properties: AppleAuthProperties,
    private val clientSecretGenerator: AppleClientSecretGenerator,
) : OAuthClient {
    override val provider = OAuthProvider.APPLE
    private val clock = Clock.systemUTC()
    private val cacheMonitor = Any()
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var jwksCache = JwksCache(Instant.EPOCH, emptyMap())

    override fun authenticate(command: LoginCommand): SocialProfile =
        (command as? LoginCommand.Apple ?: throw InvalidInputException()).run {
            val claims = verifyIdentityToken(identityToken, rawNonce)
            val refreshToken = exchangeAuthorizationCode(authorizationCode)
            SocialProfile(
                provider = provider,
                providerUserId = claims.subject,
                email = claims.email,
                providerRefreshToken = refreshToken,
                authorizationCodeExchangeFailed = refreshToken == null,
            )
        }

    override fun revoke(providerRefreshToken: String) =
        httpService.revoke(
            properties.revokeUri,
            properties.clientId,
            clientSecretGenerator.generate(),
            providerRefreshToken,
            REFRESH_TOKEN,
        )

    private fun verifyIdentityToken(
        identityToken: String,
        rawNonce: String,
    ): AppleIdentity {
        try {
            val jwt = SignedJWT.parse(identityToken)
            verifySignature(jwt)
            return extractIdentity(jwt.jwtClaimsSet, rawNonce)
        } catch (e: UnauthorizedException) {
            throw e
        } catch (e: ParseException) {
            failAuthentication(e)
        } catch (e: JOSEException) {
            failAuthentication(e)
        } catch (e: GeneralSecurityException) {
            failAuthentication(e)
        } catch (e: IllegalArgumentException) {
            failAuthentication(e)
        }
    }

    private fun verifySignature(jwt: SignedJWT) {
        if (jwt.header.algorithm != JWSAlgorithm.RS256) failAuthentication()
        val keyId = jwt.header.keyID ?: failAuthentication()
        if (!jwt.verify(RSASSAVerifier(publicKey(keyId)))) failAuthentication()
    }

    private fun extractIdentity(
        claims: JWTClaimsSet,
        rawNonce: String,
    ): AppleIdentity {
        if (claims.issuer != properties.issuer) failAuthentication()
        if (!claims.audience.contains(properties.clientId)) failAuthentication()
        if (claims.expirationTime
                ?.toInstant()
                ?.isAfter(clock.instant()) != true
        ) {
            failAuthentication()
        }
        val subject = claims.subject?.takeIf(String::isNotBlank) ?: failAuthentication()
        val email = claims.getStringClaim(EMAIL)?.takeIf(String::isNotBlank) ?: failAuthentication()
        val nonce = claims.getStringClaim(NONCE) ?: failAuthentication()
        if (!MessageDigest.isEqual(nonce.toByteArray(), hashNonce(rawNonce).toByteArray())) failAuthentication()
        return AppleIdentity(subject, email)
    }

    private fun exchangeAuthorizationCode(authorizationCode: String): String? =
        try {
            httpService
                .exchangeAuthorizationCode(
                    properties.tokenUri,
                    properties.clientId,
                    clientSecretGenerator.generate(),
                    authorizationCode,
                    AUTHORIZATION_CODE,
                )?.refreshToken
        } catch (e: RestClientException) {
            log.warn("애플 authorization code 교환에 실패했습니다.", e)
            null
        }

    private fun publicKey(keyId: String): RSAPublicKey {
        val current = jwksCache
        if (clock.instant().isBefore(current.expiresAt)) {
            current.keys[keyId]?.let { return it }
        }
        return synchronized(cacheMonitor) {
            val rechecked = jwksCache
            if (clock.instant().isBefore(rechecked.expiresAt)) {
                rechecked.keys[keyId]?.let { return@synchronized it }
            }
            refreshJwks().keys[keyId] ?: failAuthentication()
        }
    }

    private fun refreshJwks(): JwksCache {
        val response =
            try {
                httpService.getJwks(properties.jwksUri) ?: failAuthentication()
            } catch (e: RestClientException) {
                failAuthentication(e)
            }
        val keys =
            response.keys
                .filter { it.keyType == RSA && it.algorithm == RS256 }
                .associate { it.keyId to it.toPublicKey() }
        val cache = JwksCache(clock.instant().plusSeconds(properties.jwksCacheSeconds), keys)
        jwksCache = cache
        return cache
    }

    private fun AppleJwk.toPublicKey(): RSAPublicKey {
        val decoder = Base64.getUrlDecoder()
        val modulus = BigInteger(1, decoder.decode(modulus))
        val exponent = BigInteger(1, decoder.decode(exponent))
        return KeyFactory.getInstance(RSA).generatePublic(RSAPublicKeySpec(modulus, exponent)) as RSAPublicKey
    }

    private fun hashNonce(rawNonce: String): String =
        MessageDigest
            .getInstance(SHA_256)
            .digest(rawNonce.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun failAuthentication(cause: Exception? = null): Nothing {
        val exception = UnauthorizedException(AuthErrorCode.SOCIAL_AUTHENTICATION_FAILED)
        cause?.let(exception::addSuppressed)
        throw exception
    }

    private data class AppleIdentity(
        val subject: String,
        val email: String,
    )

    private data class JwksCache(
        val expiresAt: Instant,
        val keys: Map<String, RSAPublicKey>,
    )

    private companion object {
        const val AUTHORIZATION_CODE = "authorization_code"
        const val REFRESH_TOKEN = "refresh_token"
        const val EMAIL = "email"
        const val NONCE = "nonce"
        const val RSA = "RSA"
        const val RS256 = "RS256"
        const val SHA_256 = "SHA-256"
    }
}
