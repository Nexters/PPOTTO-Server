package com.github.nexters.ppotto.auth.infrastructure.security

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.github.nexters.ppotto.auth.application.port.TokenProvider
import com.github.nexters.ppotto.auth.domain.TokenPair
import com.github.nexters.ppotto.auth.infrastructure.token.JwtAccessTokenFailureReason
import com.github.nexters.ppotto.auth.infrastructure.token.JwtAccessTokenVerificationException
import com.github.nexters.ppotto.global.error.UnauthorizedException
import com.github.nexters.ppotto.global.identifier.UserId
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.slf4j.LoggerFactory
import org.springframework.mock.env.MockEnvironment
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
        val filter =
            BearerTokenAuthenticationFilter(
                tokenProvider,
                AuthAuthenticationEntryPoint(errorWriter),
                MockEnvironment().withProperty("DEPLOY_ENV", "production"),
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

        Given("Dev 환경에서 Bearer 형식이 아닌 Authorization이 주어졌을 때") {
            val appender = authLogAppender()
            val request = MockHttpServletRequest().apply { addHeader("Authorization", "Basic abc") }
            val response = MockHttpServletResponse()
            val devFilter =
                BearerTokenAuthenticationFilter(
                    tokenProvider,
                    AuthAuthenticationEntryPoint(errorWriter),
                    MockEnvironment().withProperty("DEPLOY_ENV", "dev"),
                )

            When("인증 필터를 통과하면") {
                devFilter.doFilter(request, response) { _, _ -> }

                Then("Authorization 원문과 형식 오류를 로그에 남긴다") {
                    response.status shouldBe 401
                    appender.messages().any {
                        it.contains("reason=invalid_authorization_scheme") &&
                            it.contains("authorization=Basic abc")
                    } shouldBe true
                }
            }
        }

        Given("Dev 환경에서 JWT 검증이 실패했을 때") {
            val appender = authLogAppender()
            val devTokenProvider =
                object : TokenProvider {
                    override fun issue(userId: UserId) = TokenPair("access", "refresh", 3600)

                    override fun verifyAccessToken(accessToken: String): UserId =
                        throw JwtAccessTokenVerificationException(
                            reason = JwtAccessTokenFailureReason.ISSUER_MISMATCH,
                            issuer = "ppotto-production",
                            subject = userId.toString(),
                        )
                }
            val devFilter =
                BearerTokenAuthenticationFilter(
                    devTokenProvider,
                    AuthAuthenticationEntryPoint(errorWriter),
                    MockEnvironment().withProperty("DEPLOY_ENV", "dev"),
                )
            val request = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer production-token") }
            val response = MockHttpServletResponse()

            When("인증 필터를 통과하면") {
                devFilter.doFilter(request, response) { _, _ -> }

                Then("JWT 검증 실패 사유와 Authorization 원문을 로그에 남긴다") {
                    response.status shouldBe 401
                    appender.messages().any {
                        it.contains("reason=issuer_mismatch") &&
                            it.contains("authorization=Bearer production-token") &&
                            it.contains("issuer=ppotto-production")
                    } shouldBe true
                }
            }
        }

        Given("Production 환경에서 JWT 검증이 실패했을 때") {
            val appender = authLogAppender()
            val request = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer invalid-token") }
            val response = MockHttpServletResponse()

            When("인증 필터를 통과하면") {
                filter.doFilter(request, response) { _, _ -> }

                Then("상세 인증 실패 로그를 남기지 않는다") {
                    response.status shouldBe 401
                    appender.messages().none { it.contains("auth failed") } shouldBe true
                }
            }
        }
    })

private fun authLogAppender(): ListAppender<ILoggingEvent> =
    ListAppender<ILoggingEvent>()
        .apply {
            start()
            (LoggerFactory.getLogger(BearerTokenAuthenticationFilter::class.java) as Logger).addAppender(this)
        }

private fun ListAppender<ILoggingEvent>.messages(): List<String> = list.map { it.formattedMessage }
