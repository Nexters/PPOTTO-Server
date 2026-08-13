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
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.text.ParseException
import java.time.Clock

@Component
internal class AppleOAuthClient(
    private val appleOAuthApi: AppleOAuthApi,
    private val properties: AppleAuthProperties,
    private val clientSecretGenerator: AppleClientSecretGenerator,
    private val jwkProvider: AppleJwkProvider,
) : OAuthClient {
    override val provider = OAuthProvider.APPLE
    private val clock = Clock.systemUTC()
    private val log = LoggerFactory.getLogger(javaClass)

    override fun authenticate(command: LoginCommand): SocialProfile =
        (command as? LoginCommand.Apple ?: throw InvalidInputException()).let { appleCommand ->
            verifyIdentityToken(appleCommand.identityToken, appleCommand.rawNonce).let { identity ->
                exchangeAuthorizationCode(appleCommand.authorizationCode).let { exchange ->
                    SocialProfile(
                        provider = provider,
                        providerUserId = identity.subject,
                        email = identity.email ?: exchange?.emailOf(identity.subject),
                        name = appleCommand.name,
                        providerRefreshToken = exchange?.refreshToken,
                        authorizationCodeExchangeFailed = exchange?.refreshToken == null,
                    )
                }
            }
        }

    override fun revoke(providerRefreshToken: String) {
        appleOAuthApi.revoke(
            properties.revokeUri,
            properties.clientId,
            clientSecretGenerator.generate(),
            providerRefreshToken,
            REFRESH_TOKEN,
        )
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
            failAuthentication(MALFORMED_TOKEN, e)
        } catch (e: JOSEException) {
            failAuthentication(MALFORMED_TOKEN, e)
        } catch (e: GeneralSecurityException) {
            failAuthentication(MALFORMED_TOKEN, e)
        } catch (e: IllegalArgumentException) {
            failAuthentication(MALFORMED_TOKEN, e)
        }

    private fun verifySignature(jwt: SignedJWT) {
        jwt
            .takeIf { it.header.algorithm == JWSAlgorithm.RS256 }
            ?.let {
                it.header.keyID
                    ?.let(jwkProvider::publicKey)
                    ?.let(::RSASSAVerifier)
                    ?.let(it::verify)
                    ?: false
            }.takeIf { it == true }
            ?: failAuthentication(INVALID_SIGNATURE)
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
                    ?: failAuthentication(NONCE_MISMATCH)
            }?.let {
                AppleIdentity(
                    subject = it.subject?.takeIf(String::isNotBlank) ?: failAuthentication(MISSING_SUBJECT),
                    email = it.getStringClaim(EMAIL)?.takeIf(String::isNotBlank),
                )
            } ?: failAuthentication(INVALID_CLAIMS)

    private fun exchangeAuthorizationCode(authorizationCode: String): AppleTokenResponse? =
        try {
            appleOAuthApi.exchangeToken(
                properties.tokenUri,
                properties.clientId,
                clientSecretGenerator.generate(),
                authorizationCode,
                AUTHORIZATION_CODE,
            )
        } catch (e: RestClientException) {
            log
                .warn("애플 authorization code 교환에 실패했습니다.", e)
                .let { null }
        }

    private fun AppleTokenResponse.emailOf(subject: String): String? =
        idToken?.let { token ->
            try {
                SignedJWT
                    .parse(token)
                    .also(::verifySignature)
                    .jwtClaimsSet
                    .takeIf { it.subject == subject }
                    ?.getStringClaim(EMAIL)
                    ?.takeIf(String::isNotBlank)
            } catch (e: UnauthorizedException) {
                skipExchangeEmail(e)
            } catch (e: ParseException) {
                skipExchangeEmail(e)
            } catch (e: JOSEException) {
                skipExchangeEmail(e)
            } catch (e: GeneralSecurityException) {
                skipExchangeEmail(e)
            } catch (e: IllegalArgumentException) {
                skipExchangeEmail(e)
            }
        }

    private fun skipExchangeEmail(cause: Exception): String? =
        log
            .warn("애플 code 교환 id_token에서 이메일을 확보하지 못했습니다.", cause)
            .let { null }

    private fun hashNonce(rawNonce: String): String =
        MessageDigest
            .getInstance(SHA_256)
            .digest(rawNonce.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun failAuthentication(
        reason: String,
        cause: Exception? = null,
    ): Nothing =
        UnauthorizedException(AuthErrorCode.SOCIAL_AUTHENTICATION_FAILED)
            .also { exception -> cause?.let(exception::addSuppressed) }
            .also { log.warn("애플 identity token 검증에 실패했습니다. reason={}", reason) }
            .let { throw it }

    private data class AppleIdentity(
        val subject: String,
        val email: String?,
    )

    private companion object {
        const val AUTHORIZATION_CODE = "authorization_code"
        const val REFRESH_TOKEN = "refresh_token"
        const val EMAIL = "email"
        const val NONCE = "nonce"
        const val SHA_256 = "SHA-256"
        const val MALFORMED_TOKEN = "malformed_token"
        const val INVALID_SIGNATURE = "invalid_signature"
        const val INVALID_CLAIMS = "invalid_claims"
        const val NONCE_MISMATCH = "nonce_mismatch"
        const val MISSING_SUBJECT = "missing_subject"
    }
}
