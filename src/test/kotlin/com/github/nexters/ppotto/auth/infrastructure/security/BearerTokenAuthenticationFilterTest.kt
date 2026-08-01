package com.github.nexters.ppotto.auth.infrastructure.security

import com.github.nexters.ppotto.auth.application.port.TokenProvider
import com.github.nexters.ppotto.auth.domain.TokenPair
import com.github.nexters.ppotto.global.error.UnauthorizedException
import com.github.nexters.ppotto.global.identifier.UserId
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
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
        val errorWriter =
            SecurityErrorWriter(
                JsonMapper
                    .builder()
                    .findAndAddModules()
                    .build(),
            )
        val filter = BearerTokenAuthenticationFilter(tokenProvider, AuthAuthenticationEntryPoint(errorWriter))

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
                    invoked shouldBe true
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
                    invoked shouldBe false
                    response.status shouldBe 401
                    response.contentAsString.contains("\"code\":\"COMMON-004\"") shouldBe true
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
                    invoked shouldBe true
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
                    invoked shouldBe true
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
                    invoked shouldBe false
                    response.status shouldBe 401
                    response.contentAsString.contains("\"code\":\"COMMON-004\"") shouldBe true
                }
            }
        }
    })
