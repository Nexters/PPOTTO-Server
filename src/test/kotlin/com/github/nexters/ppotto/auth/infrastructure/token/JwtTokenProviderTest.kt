package com.github.nexters.ppotto.auth.infrastructure.token

import com.github.nexters.ppotto.auth.infrastructure.config.JwtAuthProperties
import com.github.nexters.ppotto.global.error.CommonErrorCode
import com.github.nexters.ppotto.global.error.UnauthorizedException
import com.github.nexters.ppotto.global.identifier.UserId
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.PlainJWT
import com.nimbusds.jwt.SignedJWT
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Instant
import java.util.Date
import java.util.UUID

class JwtTokenProviderTest :
    BehaviorSpec({
        val issuer = "ppotto-test"
        val secret = "test-secret-that-is-at-least-32-bytes-long"
        val provider =
            JwtTokenProvider(
                JwtAuthProperties(
                    issuer = issuer,
                    secret = secret,
                    accessTokenExpirationSeconds = 3600,
                    refreshTokenExpirationDays = 30,
                ),
            )

        Given("사용자 아이디가 주어졌을 때") {
            val userId = UserId(UUID.randomUUID())

            When("서비스 토큰을 두 번 발급하면") {
                val first = provider.issue(userId)
                val second = provider.issue(userId)

                Then("access token에서 같은 사용자 아이디를 검증한다") {
                    provider.verifyAccessToken(first.accessToken) shouldBe userId
                }

                Then("access token 만료까지 남은 초를 함께 반환한다") {
                    first.accessTokenExpiresIn shouldBe 3600
                }

                Then("refresh token은 매번 다른 32바이트 랜덤 값이다") {
                    first.refreshToken.length shouldBe 43
                    first.refreshToken shouldNotBe second.refreshToken
                }
            }
        }

        Given("서명이 변경된 access token이 주어졌을 때") {
            val token = provider.issue(UserId(UUID.randomUUID())).accessToken
            val tokenParts = token.split(".")
            val signature = tokenParts.last()
            val tamperedSignature = (if (signature.first() == 'a') "b" else "a") + signature.drop(1)
            val tampered = (tokenParts.dropLast(1) + tamperedSignature).joinToString(".")

            When("access token을 검증하면") {
                val exception = shouldThrow<UnauthorizedException> { provider.verifyAccessToken(tampered) }

                Then("COMMON-004 인증 예외가 발생한다") {
                    exception.errorCode shouldBe CommonErrorCode.UNAUTHORIZED
                }
            }
        }

        Given("만료된 access token이 주어졌을 때") {
            val token =
                accessToken(
                    issuer = issuer,
                    secret = secret,
                    subject = UUID.randomUUID().toString(),
                    expiresAt = PAST,
                )

            When("access token을 검증하면") {
                val exception = shouldThrow<UnauthorizedException> { provider.verifyAccessToken(token) }

                Then("COMMON-004 인증 예외가 발생한다") {
                    exception.errorCode shouldBe CommonErrorCode.UNAUTHORIZED
                }
            }
        }

        Given("issuer가 다른 access token이 주어졌을 때") {
            val token =
                accessToken(
                    issuer = "ppotto-production",
                    secret = secret,
                    subject = UUID.randomUUID().toString(),
                )

            When("access token을 검증하면") {
                val exception = shouldThrow<UnauthorizedException> { provider.verifyAccessToken(token) }

                Then("COMMON-004 인증 예외가 발생한다") {
                    exception.errorCode shouldBe CommonErrorCode.UNAUTHORIZED
                }
            }
        }

        Given("refresh 용도 token이 access token으로 주어졌을 때") {
            val token =
                accessToken(
                    issuer = issuer,
                    secret = secret,
                    subject = UUID.randomUUID().toString(),
                    tokenUse = "refresh",
                )

            When("access token을 검증하면") {
                val exception = shouldThrow<UnauthorizedException> { provider.verifyAccessToken(token) }

                Then("COMMON-004 인증 예외가 발생한다") {
                    exception.errorCode shouldBe CommonErrorCode.UNAUTHORIZED
                }
            }
        }

        Given("subject가 UUID가 아닌 access token이 주어졌을 때") {
            val token =
                accessToken(
                    issuer = issuer,
                    secret = secret,
                    subject = "not-uuid",
                )

            When("access token을 검증하면") {
                val exception = shouldThrow<UnauthorizedException> { provider.verifyAccessToken(token) }

                Then("COMMON-004 인증 예외가 발생한다") {
                    exception.errorCode shouldBe CommonErrorCode.UNAUTHORIZED
                }
            }
        }

        Given("알고리즘이 다른 access token이 주어졌을 때") {
            val token =
                accessToken(
                    issuer = issuer,
                    secret = "$secret-extra-secret-for-hs384",
                    subject = UUID.randomUUID().toString(),
                    algorithm = JWSAlgorithm.HS384,
                )

            When("access token을 검증하면") {
                val exception = shouldThrow<UnauthorizedException> { provider.verifyAccessToken(token) }

                Then("COMMON-004 인증 예외가 발생한다") {
                    exception.errorCode shouldBe CommonErrorCode.UNAUTHORIZED
                }
            }
        }

        Given("다른 secret으로 HS256 서명한 access token이 주어졌을 때") {
            val token =
                accessToken(
                    issuer = issuer,
                    secret = "forged-secret-that-is-also-at-least-32-bytes-long",
                    subject = UUID.randomUUID().toString(),
                )

            When("access token을 검증하면") {
                val exception = shouldThrow<UnauthorizedException> { provider.verifyAccessToken(token) }

                Then("COMMON-004 인증 예외가 발생한다") {
                    exception.errorCode shouldBe CommonErrorCode.UNAUTHORIZED
                }
            }
        }

        Given("서명을 지우고 alg를 none으로 바꾼 token이 주어졌을 때") {
            val token =
                PlainJWT(
                    JWTClaimsSet
                        .Builder()
                        .issuer(issuer)
                        .subject(UUID.randomUUID().toString())
                        .issueTime(Date.from(ISSUED_AT))
                        .expirationTime(Date.from(FUTURE))
                        .jwtID(UUID.randomUUID().toString())
                        .claim("token_use", "access")
                        .build(),
                ).serialize()

            When("access token을 검증하면") {
                val exception = shouldThrow<UnauthorizedException> { provider.verifyAccessToken(token) }

                Then("COMMON-004 인증 예외가 발생한다") {
                    exception.errorCode shouldBe CommonErrorCode.UNAUTHORIZED
                }
            }
        }

        Given("JWT 형식이 아닌 token이 주어졌을 때") {
            When("access token을 검증하면") {
                val exception = shouldThrow<UnauthorizedException> { provider.verifyAccessToken("not-a-jwt") }

                Then("COMMON-004 인증 예외가 발생한다") {
                    exception.errorCode shouldBe CommonErrorCode.UNAUTHORIZED
                }
            }
        }
    })

private val FUTURE = Instant.parse("2100-01-01T00:00:00Z")
private val PAST = Instant.parse("2020-01-01T00:00:00Z")
private val ISSUED_AT = Instant.parse("2026-01-01T00:00:00Z")

private fun accessToken(
    issuer: String,
    secret: String,
    subject: String,
    expiresAt: Instant = FUTURE,
    tokenUse: String = "access",
    algorithm: JWSAlgorithm = JWSAlgorithm.HS256,
): String {
    val claims =
        JWTClaimsSet
            .Builder()
            .issuer(issuer)
            .subject(subject)
            .issueTime(Date.from(ISSUED_AT))
            .expirationTime(Date.from(expiresAt))
            .jwtID(UUID.randomUUID().toString())
            .claim("token_use", tokenUse)
            .build()
    val jwt = SignedJWT(JWSHeader.Builder(algorithm).build(), claims)
    jwt.sign(MACSigner(secret.toByteArray()))
    return jwt.serialize()
}
