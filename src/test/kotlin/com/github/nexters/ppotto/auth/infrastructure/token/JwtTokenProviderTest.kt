package com.github.nexters.ppotto.auth.infrastructure.token

import com.github.nexters.ppotto.auth.config.JwtAuthProperties
import com.github.nexters.ppotto.global.error.UnauthorizedException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

class JwtTokenProviderTest :
    BehaviorSpec({
        val provider =
            JwtTokenProvider(
                JwtAuthProperties(
                    issuer = "ppotto-test",
                    secret = "test-secret-that-is-at-least-32-bytes-long",
                    accessTokenExpirationSeconds = 3600,
                    refreshTokenExpirationDays = 30,
                ),
            )

        Given("사용자 아이디가 주어졌을 때") {
            val userId = UUID.randomUUID()

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
            val token = provider.issue(UUID.randomUUID()).accessToken
            val tampered = token.dropLast(1) + if (token.last() == 'a') "b" else "a"

            When("access token을 검증하면") {
                Then("인증 예외가 발생한다") {
                    shouldThrow<UnauthorizedException> {
                        provider.verifyAccessToken(tampered)
                    }
                }
            }
        }
    })
