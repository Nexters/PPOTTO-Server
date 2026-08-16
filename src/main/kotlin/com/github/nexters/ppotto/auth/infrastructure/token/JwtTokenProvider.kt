package com.github.nexters.ppotto.auth.infrastructure.token

import com.github.nexters.ppotto.auth.application.port.TokenProvider
import com.github.nexters.ppotto.auth.config.JwtAuthProperties
import com.github.nexters.ppotto.auth.domain.TokenPair
import com.github.nexters.ppotto.global.error.CommonErrorCode
import com.github.nexters.ppotto.global.error.UnauthorizedException
import com.github.nexters.ppotto.global.identifier.UserId
import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.text.ParseException
import java.time.Clock
import java.util.Base64
import java.util.Date
import java.util.UUID

enum class JwtAccessTokenFailureReason {
    PARSE_FAILED,
    ALGORITHM_MISMATCH,
    SIGNATURE_INVALID,
    ISSUER_MISMATCH,
    EXPIRED,
    TOKEN_USE_INVALID,
    SUBJECT_INVALID,
}

class JwtAccessTokenVerificationException(
    val reason: JwtAccessTokenFailureReason,
    val issuer: String? = null,
    val subject: String? = null,
    val tokenUse: String? = null,
    cause: Exception? = null,
) : UnauthorizedException(CommonErrorCode.UNAUTHORIZED) {
    init {
        cause?.let(::addSuppressed)
    }
}

@Component
class JwtTokenProvider(
    private val properties: JwtAuthProperties,
) : TokenProvider {
    private val clock = Clock.systemUTC()
    private val random = SecureRandom()
    private val secret = properties.secret.toByteArray(StandardCharsets.UTF_8)

    override fun issue(userId: UserId): TokenPair =
        clock
            .instant()
            .let { now ->
                JWTClaimsSet
                    .Builder()
                    .issuer(properties.issuer)
                    .subject(userId.toString())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(properties.accessTokenExpirationSeconds)))
                    .jwtID(UUID.randomUUID().toString())
                    .claim(TOKEN_USE, ACCESS)
                    .build()
            }.let { SignedJWT(JWSHeader.Builder(JWSAlgorithm.HS256).build(), it) }
            .apply { sign(MACSigner(secret)) }
            .let {
                TokenPair(
                    accessToken = it.serialize(),
                    refreshToken = randomRefreshToken(),
                    accessTokenExpiresIn = properties.accessTokenExpirationSeconds,
                )
            }

    override fun verifyAccessToken(accessToken: String): UserId =
        try {
            SignedJWT
                .parse(accessToken)
                .also { token ->
                    if (token.header.algorithm != JWSAlgorithm.HS256) {
                        unauthorized(JwtAccessTokenFailureReason.ALGORITHM_MISMATCH)
                    }
                }.also { token ->
                    if (!token.verify(MACVerifier(secret))) {
                        unauthorized(JwtAccessTokenFailureReason.SIGNATURE_INVALID)
                    }
                }.jwtClaimsSet
                .let { claims ->
                    if (claims.issuer != properties.issuer) {
                        unauthorized(JwtAccessTokenFailureReason.ISSUER_MISMATCH, issuer = claims.issuer)
                    }
                    if (claims.expirationTime
                            ?.toInstant()
                            ?.isAfter(clock.instant()) != true
                    ) {
                        unauthorized(JwtAccessTokenFailureReason.EXPIRED, subject = claims.subject)
                    }
                    claims.getStringClaim(TOKEN_USE).let { tokenUse ->
                        if (tokenUse != ACCESS) {
                            unauthorized(
                                JwtAccessTokenFailureReason.TOKEN_USE_INVALID,
                                subject = claims.subject,
                                tokenUse = tokenUse,
                            )
                        }
                    }
                    try {
                        UserId(UUID.fromString(claims.subject))
                    } catch (e: IllegalArgumentException) {
                        unauthorized(JwtAccessTokenFailureReason.SUBJECT_INVALID, subject = claims.subject, cause = e)
                    }
                }
        } catch (e: ParseException) {
            unauthorized(JwtAccessTokenFailureReason.PARSE_FAILED, cause = e)
        } catch (e: JOSEException) {
            unauthorized(JwtAccessTokenFailureReason.SIGNATURE_INVALID, cause = e)
        }

    private fun randomRefreshToken(): String =
        ByteArray(REFRESH_TOKEN_BYTES)
            .also(random::nextBytes)
            .let {
                Base64
                    .getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(it)
            }

    private fun unauthorized(
        reason: JwtAccessTokenFailureReason,
        issuer: String? = null,
        subject: String? = null,
        tokenUse: String? = null,
        cause: Exception? = null,
    ): Nothing =
        JwtAccessTokenVerificationException(
            reason = reason,
            issuer = issuer,
            subject = subject,
            tokenUse = tokenUse,
            cause = cause,
        ).let { throw it }

    private companion object {
        const val TOKEN_USE = "token_use"
        const val ACCESS = "access"
        const val REFRESH_TOKEN_BYTES = 32
    }
}
