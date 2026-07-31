package com.github.nexters.ppotto.auth.infrastructure.oauth

import com.fasterxml.jackson.annotation.JsonProperty
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
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
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
    restClientBuilder: RestClient.Builder,
    private val properties: AppleAuthProperties,
    private val clientSecretGenerator: AppleClientSecretGenerator,
) : OAuthClient {
    override val provider = OAuthProvider.APPLE
    private val restClient = restClientBuilder.build()
    private val clock = Clock.systemUTC()
    private val cacheMonitor = Any()
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var jwksCache = JwksCache(Instant.EPOCH, emptyMap())

    override fun authenticate(command: LoginCommand): SocialProfile =
        (command as? LoginCommand.Apple ?: throw InvalidInputException()).let { appleCommand ->
            verifyIdentityToken(appleCommand.identityToken, appleCommand.rawNonce).let { identity ->
                exchangeAuthorizationCode(appleCommand.authorizationCode).let { refreshToken ->
                    SocialProfile(
                        provider = provider,
                        providerUserId = identity.subject,
                        email = identity.email,
                        providerRefreshToken = refreshToken,
                        authorizationCodeExchangeFailed = refreshToken == null,
                    )
                }
            }
        }

    override fun revoke(providerRefreshToken: String) {
        LinkedMultiValueMap<String, String>()
            .apply {
                add(CLIENT_ID, properties.clientId)
                add(CLIENT_SECRET, clientSecretGenerator.generate())
                add(TOKEN, providerRefreshToken)
                add(TOKEN_TYPE_HINT, REFRESH_TOKEN)
            }.let {
                restClient
                    .post()
                    .uri(properties.revokeUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(it)
                    .retrieve()
                    .toBodilessEntity()
            }
    }

    private fun verifyIdentityToken(
        identityToken: String,
        rawNonce: String,
    ): AppleIdentity =
        try {
            SignedJWT
                .parse(identityToken)
                .also(::verifySignature)
                .let { extractIdentity(it.jwtClaimsSet, rawNonce) }
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

    private fun verifySignature(jwt: SignedJWT) {
        jwt
            .takeIf { it.header.algorithm == JWSAlgorithm.RS256 }
            ?.let {
                it.header.keyID
                    ?.let(::publicKey)
                    ?.let(::RSASSAVerifier)
                    ?.let(it::verify)
                    ?: false
            }.takeIf { it == true }
            ?: failAuthentication()
    }

    private fun extractIdentity(
        claims: JWTClaimsSet,
        rawNonce: String,
    ): AppleIdentity =
        claims
            .takeIf { it.issuer == properties.issuer }
            ?.takeIf { it.audience.contains(properties.clientId) }
            ?.takeIf {
                it.expirationTime
                    ?.toInstant()
                    ?.isAfter(clock.instant()) == true
            }?.also {
                it
                    .getStringClaim(NONCE)
                    ?.takeIf { nonce ->
                        MessageDigest.isEqual(
                            nonce.toByteArray(),
                            hashNonce(rawNonce).toByteArray(),
                        )
                    }
                    ?: failAuthentication()
            }?.let {
                AppleIdentity(
                    subject = it.subject?.takeIf(String::isNotBlank) ?: failAuthentication(),
                    email = it.getStringClaim(EMAIL)?.takeIf(String::isNotBlank) ?: failAuthentication(),
                )
            } ?: failAuthentication()

    private fun exchangeAuthorizationCode(authorizationCode: String): String? =
        LinkedMultiValueMap<String, String>()
            .apply {
                add(CLIENT_ID, properties.clientId)
                add(CLIENT_SECRET, clientSecretGenerator.generate())
                add(CODE, authorizationCode)
                add(GRANT_TYPE, AUTHORIZATION_CODE)
            }.let {
                try {
                    restClient
                        .post()
                        .uri(properties.tokenUri)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(it)
                        .retrieve()
                        .body(AppleTokenResponse::class.java)
                        ?.refreshToken
                } catch (e: RestClientException) {
                    log
                        .warn("애플 authorization code 교환에 실패했습니다.", e)
                        .let { null }
                }
            }

    private fun publicKey(keyId: String): RSAPublicKey =
        jwksCache
            .takeIf { clock.instant().isBefore(it.expiresAt) }
            ?.keys
            ?.get(keyId)
            ?: synchronized(cacheMonitor) {
                jwksCache
                    .takeIf { clock.instant().isBefore(it.expiresAt) }
                    ?.keys
                    ?.get(keyId)
                    ?: refreshJwks().keys[keyId]
                    ?: failAuthentication()
            }

    private fun refreshJwks(): JwksCache =
        (
            try {
                restClient
                    .get()
                    .uri(properties.jwksUri)
                    .retrieve()
                    .body(AppleJwksResponse::class.java)
                    ?: failAuthentication()
            } catch (e: RestClientException) {
                failAuthentication(e)
            }
        ).keys
            .filter { it.keyType == RSA && it.algorithm == RS256 }
            .associate { it.keyId to it.toPublicKey() }
            .let { JwksCache(clock.instant().plusSeconds(properties.jwksCacheSeconds), it) }
            .also { jwksCache = it }

    private fun AppleJwk.toPublicKey(): RSAPublicKey =
        Base64
            .getUrlDecoder()
            .let {
                RSAPublicKeySpec(
                    BigInteger(1, it.decode(modulus)),
                    BigInteger(1, it.decode(exponent)),
                )
            }.let { KeyFactory.getInstance(RSA).generatePublic(it) as RSAPublicKey }

    private fun hashNonce(rawNonce: String): String =
        MessageDigest
            .getInstance(SHA_256)
            .digest(rawNonce.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun failAuthentication(cause: Exception? = null): Nothing =
        UnauthorizedException(AuthErrorCode.SOCIAL_AUTHENTICATION_FAILED)
            .also { exception -> cause?.let(exception::addSuppressed) }
            .let { throw it }

    private data class AppleIdentity(
        val subject: String,
        val email: String,
    )

    private data class AppleTokenResponse(
        @JsonProperty("refresh_token")
        val refreshToken: String?,
    )

    private data class AppleJwksResponse(
        val keys: List<AppleJwk>,
    )

    private data class AppleJwk(
        @JsonProperty("kty")
        val keyType: String,
        @JsonProperty("kid")
        val keyId: String,
        @JsonProperty("alg")
        val algorithm: String,
        @JsonProperty("n")
        val modulus: String,
        @JsonProperty("e")
        val exponent: String,
    )

    private data class JwksCache(
        val expiresAt: Instant,
        val keys: Map<String, RSAPublicKey>,
    )

    private companion object {
        const val CLIENT_ID = "client_id"
        const val CLIENT_SECRET = "client_secret"
        const val TOKEN = "token"
        const val TOKEN_TYPE_HINT = "token_type_hint"
        const val CODE = "code"
        const val GRANT_TYPE = "grant_type"
        const val AUTHORIZATION_CODE = "authorization_code"
        const val REFRESH_TOKEN = "refresh_token"
        const val EMAIL = "email"
        const val NONCE = "nonce"
        const val RSA = "RSA"
        const val RS256 = "RS256"
        const val SHA_256 = "SHA-256"
    }
}
