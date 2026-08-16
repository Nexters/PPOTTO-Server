package com.github.nexters.ppotto.auth.infrastructure.token

import com.github.nexters.ppotto.auth.config.JwtAuthProperties
import com.github.nexters.ppotto.global.identifier.UserId
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
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

            When("서비스 토큰을 발급하면") {
                Then("access token에서 같은 사용자 아이디를 검증하고 refresh token은 매번 다르다") {
                    val first = provider.issue(userId)
                    val second = provider.issue(userId)

                    provider.verifyAccessToken(first.accessToken) shouldBe userId
                    first.refreshToken.length shouldBe 43
                    (first.refreshToken == second.refreshToken) shouldBe false
                    first.accessTokenExpiresIn shouldBe 3600
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
                Then("인증 예외가 발생한다") {
                    shouldThrow<JwtAccessTokenVerificationException> {
                        provider.verifyAccessToken(tampered)
                    }.reason shouldBe JwtAccessTokenFailureReason.SIGNATURE_INVALID
                }
            }
        }

        Given("만료된 access token이 주어졌을 때") {
            val token =
                accessToken(
                    issuer = issuer,
                    secret = secret,
                    subject = UUID.randomUUID().toString(),
                    expiresAt = Instant.now().minusSeconds(1),
                )

            When("access token을 검증하면") {
                Then("만료 사유의 인증 예외가 발생한다") {
                    shouldThrow<JwtAccessTokenVerificationException> {
                        provider.verifyAccessToken(token)
                    }.reason shouldBe JwtAccessTokenFailureReason.EXPIRED
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
                Then("issuer 불일치 사유의 인증 예외가 발생한다") {
                    shouldThrow<JwtAccessTokenVerificationException> {
                        provider.verifyAccessToken(token)
                    }.let {
                        it.reason shouldBe JwtAccessTokenFailureReason.ISSUER_MISMATCH
                        it.issuer shouldBe "ppotto-production"
                    }
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
                Then("token_use 불일치 사유의 인증 예외가 발생한다") {
                    shouldThrow<JwtAccessTokenVerificationException> {
                        provider.verifyAccessToken(token)
                    }.let {
                        it.reason shouldBe JwtAccessTokenFailureReason.TOKEN_USE_INVALID
                        it.tokenUse shouldBe "refresh"
                    }
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
                Then("subject 변환 실패 사유의 인증 예외가 발생한다") {
                    shouldThrow<JwtAccessTokenVerificationException> {
                        provider.verifyAccessToken(token)
                    }.let {
                        it.reason shouldBe JwtAccessTokenFailureReason.SUBJECT_INVALID
                        it.subject shouldBe "not-uuid"
                    }
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
                Then("알고리즘 불일치 사유의 인증 예외가 발생한다") {
                    shouldThrow<JwtAccessTokenVerificationException> {
                        provider.verifyAccessToken(token)
                    }.reason shouldBe JwtAccessTokenFailureReason.ALGORITHM_MISMATCH
                }
            }
        }

        Given("JWT 형식이 아닌 token이 주어졌을 때") {
            When("access token을 검증하면") {
                Then("파싱 실패 사유의 인증 예외가 발생한다") {
                    shouldThrow<JwtAccessTokenVerificationException> {
                        provider.verifyAccessToken("not-a-jwt")
                    }.reason shouldBe JwtAccessTokenFailureReason.PARSE_FAILED
                }
            }
        }
    })

private fun accessToken(
    issuer: String,
    secret: String,
    subject: String,
    expiresAt: Instant = Instant.now().plusSeconds(3600),
    tokenUse: String = "access",
    algorithm: JWSAlgorithm = JWSAlgorithm.HS256,
): String =
    JWTClaimsSet
        .Builder()
        .issuer(issuer)
        .subject(subject)
        .issueTime(Date.from(Instant.now()))
        .expirationTime(Date.from(expiresAt))
        .jwtID(UUID.randomUUID().toString())
        .claim("token_use", tokenUse)
        .build()
        .let { SignedJWT(JWSHeader.Builder(algorithm).build(), it) }
        .apply { sign(MACSigner(secret.toByteArray())) }
        .serialize()
