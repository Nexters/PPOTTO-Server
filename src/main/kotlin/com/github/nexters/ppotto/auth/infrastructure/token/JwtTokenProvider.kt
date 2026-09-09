package com.github.nexters.ppotto.auth.infrastructure.token

import com.github.nexters.ppotto.auth.application.port.TokenProvider
import com.github.nexters.ppotto.auth.config.JwtAuthProperties
import com.github.nexters.ppotto.auth.domain.TokenPair
import com.github.nexters.ppotto.global.error.UnauthorizedException
import com.github.nexters.ppotto.global.identifier.UserId
import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.text.ParseException
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID

@Component
class JwtTokenProvider(
    private val properties: JwtAuthProperties,
) : TokenProvider {
    private val log = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()
    private val secret = properties.secret.toByteArray(StandardCharsets.UTF_8)

    override fun issue(userId: UserId): TokenPair {
        val now = Instant.now()
        val claims =
            JWTClaimsSet
                .Builder()
                .issuer(properties.issuer)
                .subject(userId.toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(properties.accessTokenExpirationSeconds)))
                .jwtID(UUID.randomUUID().toString())
                .claim(TOKEN_USE, ACCESS)
                .build()
        val jwt = SignedJWT(JWSHeader.Builder(JWSAlgorithm.HS256).build(), claims)
        jwt.sign(MACSigner(secret))
        return TokenPair(
            accessToken = jwt.serialize(),
            refreshToken = randomRefreshToken(),
            accessTokenExpiresIn = properties.accessTokenExpirationSeconds,
        )
    }

    override fun verifyAccessToken(accessToken: String): UserId {
        try {
            val jwt = SignedJWT.parse(accessToken)
            if (jwt.header.algorithm != JWSAlgorithm.HS256) {
                unauthorized(ALGORITHM_MISMATCH)
            }
            if (!jwt.verify(MACVerifier(secret))) {
                unauthorized(SIGNATURE_INVALID)
            }

            val claims = jwt.jwtClaimsSet
            if (claims.issuer != properties.issuer) {
                unauthorized(ISSUER_MISMATCH)
            }
            if (claims.expirationTime
                    ?.toInstant()
                    ?.isAfter(Instant.now()) != true
            ) {
                unauthorized(EXPIRED)
            }
            if (claims.getStringClaim(TOKEN_USE) != ACCESS) {
                unauthorized(TOKEN_USE_INVALID)
            }
            return subjectUserId(claims.subject)
        } catch (e: ParseException) {
            unauthorized(PARSE_FAILED, e)
        } catch (e: JOSEException) {
            unauthorized(SIGNATURE_INVALID, e)
        }
    }

    private fun subjectUserId(subject: String?): UserId =
        try {
            UserId(UUID.fromString(subject))
        } catch (e: IllegalArgumentException) {
            unauthorized(SUBJECT_INVALID, e)
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
        reason: String,
        cause: Exception? = null,
    ): Nothing {
        log.info("access token 검증에 실패했습니다. reason={}", reason)
        throw UnauthorizedException(cause = cause)
    }

    private companion object {
        const val TOKEN_USE = "token_use"
        const val ACCESS = "access"
        const val REFRESH_TOKEN_BYTES = 32
        const val PARSE_FAILED = "parse_failed"
        const val ALGORITHM_MISMATCH = "algorithm_mismatch"
        const val SIGNATURE_INVALID = "signature_invalid"
        const val ISSUER_MISMATCH = "issuer_mismatch"
        const val EXPIRED = "expired"
        const val TOKEN_USE_INVALID = "token_use_invalid"
        const val SUBJECT_INVALID = "subject_invalid"
    }
}
