package com.github.nexters.ppotto.auth.infrastructure.token

import com.github.nexters.ppotto.auth.application.port.TokenProvider
import com.github.nexters.ppotto.auth.config.JwtAuthProperties
import com.github.nexters.ppotto.auth.domain.TokenPair
import com.github.nexters.ppotto.global.error.CommonErrorCode
import com.github.nexters.ppotto.global.error.UnauthorizedException
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

@Component
class JwtTokenProvider(
    private val properties: JwtAuthProperties,
) : TokenProvider {
    private val clock = Clock.systemUTC()
    private val random = SecureRandom()
    private val secret = properties.secret.toByteArray(StandardCharsets.UTF_8)

    override fun issue(userId: UUID): TokenPair =
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

    override fun verifyAccessToken(accessToken: String): UUID =
        try {
            SignedJWT
                .parse(accessToken)
                .also {
                    if (it.header.algorithm != JWSAlgorithm.HS256 || !it.verify(MACVerifier(secret))) unauthorized()
                }.let { it.jwtClaimsSet }
                .also {
                    if (it.issuer != properties.issuer) unauthorized()
                    if (it.expirationTime
                            ?.toInstant()
                            ?.isAfter(clock.instant()) != true
                    ) {
                        unauthorized()
                    }
                    if (it.getStringClaim(TOKEN_USE) != ACCESS) unauthorized()
                }.let { UUID.fromString(it.subject) }
        } catch (e: UnauthorizedException) {
            throw e
        } catch (e: ParseException) {
            unauthorized(e)
        } catch (e: JOSEException) {
            unauthorized(e)
        } catch (e: IllegalArgumentException) {
            unauthorized(e)
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

    private fun unauthorized(cause: Exception? = null): Nothing =
        UnauthorizedException(CommonErrorCode.UNAUTHORIZED)
            .also { exception -> cause?.let(exception::addSuppressed) }
            .let { throw it }

    private companion object {
        const val TOKEN_USE = "token_use"
        const val ACCESS = "access"
        const val REFRESH_TOKEN_BYTES = 32
    }
}
