package com.github.nexters.ppotto.auth.infrastructure.oauth

import com.github.nexters.ppotto.auth.application.port.OAuthClient
import com.github.nexters.ppotto.auth.domain.AuthErrorCode
import com.github.nexters.ppotto.auth.domain.LoginCommand
import com.github.nexters.ppotto.auth.domain.SocialProfile
import com.github.nexters.ppotto.auth.infrastructure.config.AppleAuthProperties
import com.github.nexters.ppotto.auth.infrastructure.sha256Hex
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.error.UnauthorizedException
import com.github.nexters.ppotto.global.oauth.OAuthProvider
import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.text.ParseException
import java.time.Instant

@Component
internal class AppleOAuthClient(
    private val appleOAuthApi: AppleOAuthApi,
    private val properties: AppleAuthProperties,
    private val clientSecretGenerator: AppleClientSecretGenerator,
    private val jwkProvider: AppleJwkProvider,
) : OAuthClient {
    override val provider = OAuthProvider.APPLE
    private val log = LoggerFactory.getLogger(javaClass)

    override fun authenticate(command: LoginCommand): SocialProfile {
        val appleCommand = command as? LoginCommand.Apple ?: throw InvalidInputException()
        val identity = verifyIdentityToken(appleCommand.identityToken, appleCommand.rawNonce)
        val exchange = exchangeAuthorizationCode(appleCommand.authorizationCode)
        return SocialProfile(
            provider = provider,
            providerUserId = identity.subject,
            email = identity.email ?: exchange?.emailOf(identity.subject),
            name = appleCommand.name,
            providerRefreshToken = exchange?.refreshToken,
            authorizationCodeExchangeFailed = exchange?.refreshToken == null,
        )
    }

    override fun revoke(providerRefreshToken: String) {
        try {
            appleOAuthApi.revoke(
                properties.revokeUri,
                properties.clientId,
                clientSecretGenerator.generate(),
                providerRefreshToken,
                REFRESH_TOKEN,
            )
        } catch (e: HttpClientErrorException) {
            log.warn("애플이 계정 해지를 거절했습니다. 이미 해지된 토큰으로 간주하고 탈퇴를 계속합니다.", e)
        }
    }

    private fun verifyIdentityToken(
        identityToken: String,
        rawNonce: String,
    ): AppleIdentity =
        try {
            val jwt = SignedJWT.parse(identityToken)
            verifySignature(jwt)
            extractIdentity(jwt.jwtClaimsSet, rawNonce)
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
        if (jwt.header.algorithm != JWSAlgorithm.RS256) {
            failAuthentication(INVALID_SIGNATURE)
        }
        val keyId = jwt.header.keyID ?: failAuthentication(INVALID_SIGNATURE)
        val publicKey = jwkProvider.publicKey(keyId) ?: failAuthentication(INVALID_SIGNATURE)
        if (!jwt.verify(RSASSAVerifier(publicKey))) {
            failAuthentication(INVALID_SIGNATURE)
        }
    }

    private fun extractIdentity(
        claims: JWTClaimsSet,
        rawNonce: String,
    ): AppleIdentity {
        if (claims.issuer != properties.issuer) {
            failAuthentication(ISSUER_MISMATCH)
        }
        if (!claims.audience.contains(properties.clientId)) {
            failAuthentication(AUDIENCE_MISMATCH)
        }
        if (claims.expirationTime
                ?.toInstant()
                ?.isAfter(Instant.now()) != true
        ) {
            failAuthentication(EXPIRED)
        }
        val nonce = claims.getStringClaim(NONCE)
        if (nonce == null || !MessageDigest.isEqual(nonce.toByteArray(), rawNonce.sha256Hex().toByteArray())) {
            failAuthentication(NONCE_MISMATCH)
        }
        return AppleIdentity(
            subject = claims.subject?.takeIf(String::isNotBlank) ?: failAuthentication(MISSING_SUBJECT),
            email = claims.getStringClaim(EMAIL)?.takeIf(String::isNotBlank),
        )
    }

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
            log.warn("애플 authorization code 교환에 실패했습니다.", e)
            null
        }

    private fun AppleTokenResponse.emailOf(subject: String): String? {
        val token = idToken ?: return null
        return try {
            val jwt = SignedJWT.parse(token)
            verifySignature(jwt)
            val claims = jwt.jwtClaimsSet
            if (claims.subject == subject) claims.getStringClaim(EMAIL)?.takeIf(String::isNotBlank) else null
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

    private fun skipExchangeEmail(cause: Exception): String? {
        log.warn("애플 code 교환 id_token에서 이메일을 확보하지 못했습니다.", cause)
        return null
    }

    private fun failAuthentication(
        reason: String,
        cause: Exception? = null,
    ): Nothing {
        log.warn("애플 identity token 검증에 실패했습니다. reason={}", reason)
        throw UnauthorizedException(AuthErrorCode.SOCIAL_AUTHENTICATION_FAILED, cause = cause)
    }

    private data class AppleIdentity(
        val subject: String,
        val email: String?,
    )

    private companion object {
        const val AUTHORIZATION_CODE = "authorization_code"
        const val REFRESH_TOKEN = "refresh_token"
        const val EMAIL = "email"
        const val NONCE = "nonce"
        const val MALFORMED_TOKEN = "malformed_token"
        const val INVALID_SIGNATURE = "invalid_signature"
        const val ISSUER_MISMATCH = "issuer_mismatch"
        const val AUDIENCE_MISMATCH = "audience_mismatch"
        const val EXPIRED = "expired"
        const val NONCE_MISMATCH = "nonce_mismatch"
        const val MISSING_SUBJECT = "missing_subject"
    }
}
