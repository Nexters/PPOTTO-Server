package com.github.nexters.ppotto.auth.infrastructure.security

import com.github.nexters.ppotto.auth.application.port.TokenProvider
import com.github.nexters.ppotto.auth.domain.TokenPair
import com.github.nexters.ppotto.global.error.CommonErrorCode
import com.github.nexters.ppotto.global.error.UnauthorizedException
import com.github.nexters.ppotto.global.identifier.UserId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

class BearerTokenAuthenticationFilterTest :
    BehaviorSpec({
        val userId = UUID.randomUUID()
        val tokenProvider =
            object : TokenProvider {
                override fun issue(userId: UserId) = TokenPair("access", "refresh", 3600)

                override fun verifyAccessToken(accessToken: String): UserId {
                    if (accessToken != "valid-token") throw UnauthorizedException()
                    return UserId(userId)
                }
            }
        val filter =
            BearerTokenAuthenticationFilter(
                tokenProvider,
                AuthAuthenticationEntryPoint(
                    JsonMapper
                        .builder()
                        .findAndAddModules()
                        .build(),
                ),
            )

        afterEach {
            SecurityContextHolder.clearContext()
        }

        Given("유효한 Bearer access token이 주어졌을 때") {
            val request = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer valid-token") }
            val response = MockHttpServletResponse()
            var invoked = false

            When("인증 필터를 통과하면") {
                filter.doFilter(request, response) { _, _ -> invoked = true }

                Then("Authentication principal에 UUID 자체가 저장된다") {
                    invoked.shouldBeTrue()
                    SecurityContextHolder
                        .getContext()
                        .authentication
                        ?.principal shouldBe userId
                }
            }
        }

        Given("위조된 Bearer access token이 주어졌을 때") {
            val request = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer invalid-token") }
            val response = MockHttpServletResponse()
            var invoked = false

            When("인증 필터를 통과하면") {
                filter.doFilter(request, response) { _, _ -> invoked = true }

                Then("COMMON-004 ApiResponse와 401을 반환한다") {
                    invoked.shouldBeFalse()
                    response.status shouldBe 401
                    response.contentAsString shouldContain "\"code\":\"COMMON-004\""
                }
            }
        }

        Given("Bearer 형식이 아닌 Authorization이 주어졌을 때") {
            val request = MockHttpServletRequest().apply { addHeader("Authorization", "Basic abc") }
            val response = MockHttpServletResponse()
            var invoked = false

            When("인증 필터를 통과하면") {
                filter.doFilter(request, response) { _, _ -> invoked = true }

                Then("COMMON-004 ApiResponse와 401을 반환한다") {
                    invoked.shouldBeFalse()
                    response.status shouldBe 401
                    response.contentAsString shouldContain "\"code\":\"COMMON-004\""
                }
            }
        }

        Given("Bearer 뒤 토큰이 비어 있을 때") {
            val request = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer   ") }
            val response = MockHttpServletResponse()
            var invoked = false

            When("인증 필터를 통과하면") {
                filter.doFilter(request, response) { _, _ -> invoked = true }

                Then("COMMON-004 ApiResponse와 401을 반환한다") {
                    invoked.shouldBeFalse()
                    response.status shouldBe 401
                    response.contentAsString shouldContain "\"code\":\"COMMON-004\""
                }
            }
        }

        Given("access token 없이 공개 로그인 경로를 호출할 때") {
            val request = MockHttpServletRequest("POST", "/auth/login").apply { servletPath = "/auth/login" }
            val response = MockHttpServletResponse()
            var invoked = false

            When("인증 필터를 통과하면") {
                filter.doFilter(request, response) { _, _ -> invoked = true }

                Then("인증 없이 다음 필터로 진행한다") {
                    invoked.shouldBeTrue()
                }
            }
        }

        Given("access token 없이 공개 약관 경로를 호출할 때") {
            val request = MockHttpServletRequest("GET", "/terms").apply { servletPath = "/terms" }
            val response = MockHttpServletResponse()
            var invoked = false

            When("인증 필터를 통과하면") {
                filter.doFilter(request, response) { _, _ -> invoked = true }

                Then("익명 요청으로 다음 필터에 진행한다") {
                    invoked.shouldBeTrue()
                }
            }
        }

        Given("유효하지 않은 access token으로 공개 약관 경로를 호출할 때") {
            val request =
                MockHttpServletRequest("GET", "/terms").apply {
                    servletPath = "/terms"
                    addHeader("Authorization", "Bearer invalid-token")
                }
            val response = MockHttpServletResponse()
            var invoked = false

            When("인증 필터를 통과하면") {
                filter.doFilter(request, response) { _, _ -> invoked = true }

                Then("COMMON-004 ApiResponse와 401을 반환한다") {
                    invoked.shouldBeFalse()
                    response.status shouldBe 401
                    response.contentAsString shouldContain "\"code\":\"COMMON-004\""
                }
            }
        }

        Given("다음 필터가 인증 예외를 던지는 요청일 때") {
            val request = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer valid-token") }
            val response = MockHttpServletResponse()

            When("인증 필터를 통과하면") {
                val thrown =
                    shouldThrow<UnauthorizedException> {
                        filter.doFilter(request, response) { _, _ -> throw UnauthorizedException() }
                    }

                Then("필터가 삼키지 않고 그대로 전파해 401 응답을 쓰지 않는다") {
                    thrown.errorCode shouldBe CommonErrorCode.UNAUTHORIZED
                    response.status shouldBe 200
                }
            }
        }
    })
