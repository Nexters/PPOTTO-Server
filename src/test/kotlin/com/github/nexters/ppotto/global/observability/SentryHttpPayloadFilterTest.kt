package com.github.nexters.ppotto.global.observability

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.protocol.SentryTransaction
import jakarta.servlet.http.HttpServletResponse
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.util.concurrent.CopyOnWriteArrayList

class SentryHttpPayloadFilterTest :
    BehaviorSpec({
        val captured = CopyOnWriteArrayList<SentryTransaction>()
        val filter = SentryHttpPayloadFilter()

        beforeSpec {
            Sentry.init { options ->
                options.dsn = TEST_DSN
                options.tracesSampleRate = 1.0
                options.isEnableUncaughtExceptionHandler = false
                options.isEnableBackpressureHandling = false
                options.isEnableAutoSessionTracking = false
                options.beforeSend = SentryOptions.BeforeSendCallback { _, _ -> null }
                options.beforeSendTransaction =
                    SentryOptions.BeforeSendTransactionCallback { transaction, _ ->
                        captured.add(transaction)
                        null
                    }
            }
        }

        afterSpec { Sentry.close() }

        Given("인증 요청과 토큰이 담긴 응답이면") {
            captured.clear()
            val request =
                MockHttpServletRequest("POST", "/auth/login").apply {
                    servletPath = "/auth/login"
                    contentType = "application/json"
                    characterEncoding = "UTF-8"
                    addHeader("Authorization", "Bearer real-access-token")
                    addHeader("X-API-Version", "1")
                    setContent("""{"provider":"KAKAO","accessToken":"kakao-oauth-token"}""".toByteArray())
                }
            val response = MockHttpServletResponse()
            val responseBody = """{"data":{"accessToken":"real-jwt","refreshToken":"real-refresh","userId":"u-1"}}"""
            val chain =
                MockFilterChain(
                    object : jakarta.servlet.http.HttpServlet() {
                        override fun service(
                            req: jakarta.servlet.ServletRequest,
                            res: jakarta.servlet.ServletResponse,
                        ) {
                            req.inputStream.readAllBytes()
                            (res as HttpServletResponse).contentType = "application/json"
                            res.characterEncoding = "UTF-8"
                            res.addHeader("Set-Cookie", "refresh=real-refresh")
                            res.writer.write(responseBody)
                            res.status = 200
                        }
                    },
                )

            When("트랜잭션이 열린 상태에서 필터를 태우면") {
                val transaction = Sentry.startTransaction("POST /auth/login", "http.server")
                transaction.makeCurrent().use {
                    filter.doFilter(request, response, chain)
                }
                transaction.finish()

                Then("응답 본문이 클라이언트에 그대로 전달된다") {
                    response.contentAsString shouldBe responseBody
                }

                Then("요청/응답 헤더가 트랜잭션 span에 붙는다") {
                    val data =
                        captured
                            .single()
                            .contexts.trace
                            .shouldNotBeNull()
                            .data

                    data["http.request.header.authorization"] shouldBe "[Filtered]"
                    data["http.request.header.x-api-version"] shouldBe "1"
                    data["http.response.header.set-cookie"] shouldBe "[Filtered]"
                }

                Then("요청 본문의 OAuth 토큰을 마스킹해 담는다") {
                    val data =
                        captured
                            .single()
                            .contexts.trace
                            .shouldNotBeNull()
                            .data
                    val body = data["http.request.body.data"] as String

                    body shouldNotContain "kakao-oauth-token"
                    body shouldContain "[Filtered]"
                    body shouldContain "KAKAO"
                }

                Then("응답 본문의 JWT를 마스킹해 담는다") {
                    val data =
                        captured
                            .single()
                            .contexts.trace
                            .shouldNotBeNull()
                            .data
                    val body = data["http.response.body.data"] as String

                    body shouldNotContain "real-jwt"
                    body shouldNotContain "real-refresh"
                    body shouldContain "[Filtered]"
                    body shouldContain "u-1"
                }
            }
        }

        Given("앱이 본문을 읽기 전에 거부한 요청이면") {
            captured.clear()
            val request =
                MockHttpServletRequest("POST", "/boards").apply {
                    servletPath = "/boards"
                    contentType = "application/json"
                    characterEncoding = "UTF-8"
                    setContent("""{"name":"거부된 보드","password":"p@ss"}""".toByteArray())
                }
            val response = MockHttpServletResponse()
            val chain =
                MockFilterChain(
                    object : jakarta.servlet.http.HttpServlet() {
                        override fun service(
                            req: jakarta.servlet.ServletRequest,
                            res: jakarta.servlet.ServletResponse,
                        ) {
                            (res as HttpServletResponse).status = 401
                        }
                    },
                )

            When("트랜잭션이 열린 상태에서 필터를 태우면") {
                val transaction = Sentry.startTransaction("POST /boards", "http.server")
                transaction.makeCurrent().use {
                    filter.doFilter(request, response, chain)
                }
                transaction.finish()

                Then("읽히지 않은 요청 본문도 마스킹해 담는다") {
                    val body =
                        captured
                            .single()
                            .contexts.trace
                            .shouldNotBeNull()
                            .data["http.request.body.data"] as String

                    body shouldContain "거부된 보드"
                    body shouldNotContain "p@ss"
                    body shouldContain "[Filtered]"
                }
            }
        }

        Given("swagger 문서 요청이면") {
            val request = MockHttpServletRequest("GET", "/swagger-ui/index.html").apply { servletPath = "/swagger-ui/index.html" }

            When("필터 적용 여부를 판정하면") {
                Then("응답 버퍼링을 하지 않도록 건너뛴다") {
                    filter.shouldNotFilterFor(request).shouldBeTrue()
                }
            }
        }

        Given("actuator 요청이면") {
            val request = MockHttpServletRequest("GET", "/actuator/health").apply { servletPath = "/actuator/health" }

            When("필터 적용 여부를 판정하면") {
                Then("건너뛴다") {
                    filter.shouldNotFilterFor(request).shouldBeTrue()
                }
            }
        }

        Given("활성 트랜잭션이 없으면") {
            val request =
                MockHttpServletRequest("GET", "/boards").apply {
                    servletPath = "/boards"
                }
            val response = MockHttpServletResponse()
            val chain =
                MockFilterChain(
                    object : jakarta.servlet.http.HttpServlet() {
                        override fun service(
                            req: jakarta.servlet.ServletRequest,
                            res: jakarta.servlet.ServletResponse,
                        ) {
                            res.writer.write("""{"ok":true}""")
                        }
                    },
                )

            When("필터를 태우면") {
                filter.doFilter(request, response, chain)

                Then("버퍼링 없이 응답을 그대로 흘려보낸다") {
                    response.contentAsString shouldBe """{"ok":true}"""
                }
            }
        }
    })

private fun SentryHttpPayloadFilter.shouldNotFilterFor(request: MockHttpServletRequest): Boolean =
    SentryHttpPayloadFilter::class.java
        .getDeclaredMethod("shouldNotFilter", jakarta.servlet.http.HttpServletRequest::class.java)
        .apply { isAccessible = true }
        .invoke(this, request) as Boolean

private const val TEST_DSN = "https://public@localhost/1"
