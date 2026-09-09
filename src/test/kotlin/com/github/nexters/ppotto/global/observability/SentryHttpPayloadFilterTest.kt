package com.github.nexters.ppotto.global.observability

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.sentry.Sentry
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class SentryHttpPayloadFilterTest :
    BehaviorSpec({
        val sentry = withSentry()
        val filter = SentryHttpPayloadFilter()

        Given("인증 요청과 토큰이 담긴 응답이면") {
            sentry.clear()
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
                servletChain { req, res ->
                    req.inputStream.readAllBytes()
                    res.contentType = "application/json"
                    res.characterEncoding = "UTF-8"
                    res.addHeader("Set-Cookie", "refresh=real-refresh")
                    res.writer.write(responseBody)
                    res.status = 200
                }

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
                    val data = sentry.singleTransactionData()

                    data["http.request.header.authorization"] shouldBe "[Filtered]"
                    data["http.request.header.x-api-version"] shouldBe "1"
                    data["http.response.header.set-cookie"] shouldBe "[Filtered]"
                }

                Then("요청 본문의 OAuth 토큰을 마스킹해 담는다") {
                    val body = sentry.singleTransactionData()["http.request.body.data"] as String

                    body shouldNotContain "kakao-oauth-token"
                    body shouldContain "[Filtered]"
                    body shouldContain "KAKAO"
                }

                Then("응답 본문의 JWT를 마스킹해 담는다") {
                    val body = sentry.singleTransactionData()["http.response.body.data"] as String

                    body shouldNotContain "real-jwt"
                    body shouldNotContain "real-refresh"
                    body shouldContain "[Filtered]"
                    body shouldContain "u-1"
                }
            }
        }

        Given("앱이 본문을 읽기 전에 거부한 요청이면") {
            sentry.clear()
            val request =
                MockHttpServletRequest("POST", "/boards").apply {
                    servletPath = "/boards"
                    contentType = "application/json"
                    characterEncoding = "UTF-8"
                    setContent("""{"name":"거부된 보드","password":"p@ss"}""".toByteArray())
                }
            val response = MockHttpServletResponse()
            val chain = servletChain { _, res -> res.status = 401 }

            When("트랜잭션이 열린 상태에서 필터를 태우면") {
                val transaction = Sentry.startTransaction("POST /boards", "http.server")
                transaction.makeCurrent().use {
                    filter.doFilter(request, response, chain)
                }
                transaction.finish()

                Then("읽히지 않은 요청 본문도 마스킹해 담는다") {
                    val body = sentry.singleTransactionData()["http.request.body.data"] as String

                    body shouldContain "거부된 보드"
                    body shouldNotContain "p@ss"
                    body shouldContain "[Filtered]"
                }
            }
        }

        Given("swagger 문서 요청이면") {
            sentry.clear()
            val request = jsonRequest("GET", "/swagger-ui/index.html")
            val response = MockHttpServletResponse()

            When("트랜잭션이 열린 상태에서 필터를 태우면") {
                val transaction = Sentry.startTransaction("GET /swagger-ui/index.html", "http.server")
                transaction.makeCurrent().use {
                    filter.doFilter(request, response, jsonEchoChain())
                }
                transaction.finish()

                Then("응답을 버퍼링하지 않고 그대로 흘려보낸다") {
                    response.contentAsString shouldBe ECHO_BODY
                }

                Then("span에 http.* 속성을 하나도 남기지 않는다") {
                    sentry
                        .singleTransactionData()
                        .keys
                        .none { it.startsWith("http.") } shouldBe true
                }
            }
        }

        Given("actuator 요청이면") {
            sentry.clear()
            val request = jsonRequest("GET", "/actuator/health")
            val response = MockHttpServletResponse()

            When("트랜잭션이 열린 상태에서 필터를 태우면") {
                val transaction = Sentry.startTransaction("GET /actuator/health", "http.server")
                transaction.makeCurrent().use {
                    filter.doFilter(request, response, jsonEchoChain())
                }
                transaction.finish()

                Then("헬스체크 본문은 span에 담지 않는다") {
                    sentry
                        .singleTransactionData()
                        .keys
                        .none { it.startsWith("http.") } shouldBe true
                }
            }
        }

        Given("활성 트랜잭션이 없으면") {
            val request =
                MockHttpServletRequest("GET", "/boards").apply {
                    servletPath = "/boards"
                }
            val response = MockHttpServletResponse()
            val chain = servletChain { _, res -> res.writer.write("""{"ok":true}""") }

            When("필터를 태우면") {
                filter.doFilter(request, response, chain)

                Then("버퍼링 없이 응답을 그대로 흘려보낸다") {
                    response.contentAsString shouldBe """{"ok":true}"""
                }
            }
        }
    })

private const val ECHO_BODY = """{"ok":true}"""

private fun jsonRequest(
    method: String,
    path: String,
): MockHttpServletRequest =
    MockHttpServletRequest(method, path).apply {
        servletPath = path
        contentType = "application/json"
        characterEncoding = "UTF-8"
        setContent("""{"marker":"필터-건너뜀"}""".toByteArray())
    }

private fun jsonEchoChain(): MockFilterChain =
    servletChain { _, res ->
        res.contentType = "application/json"
        res.characterEncoding = "UTF-8"
        res.writer.write(ECHO_BODY)
        res.status = 200
    }

private fun servletChain(handle: (HttpServletRequest, HttpServletResponse) -> Unit): MockFilterChain =
    MockFilterChain(
        object : HttpServlet() {
            override fun service(
                req: ServletRequest,
                res: ServletResponse,
            ) {
                handle(req as HttpServletRequest, res as HttpServletResponse)
            }
        },
    )
