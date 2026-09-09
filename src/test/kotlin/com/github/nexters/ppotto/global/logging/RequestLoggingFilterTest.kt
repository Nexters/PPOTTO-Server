package com.github.nexters.ppotto.global.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.slf4j.LoggerFactory
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

private const val SECRET = "raw-secret-value"

class RequestLoggingFilterTest :
    BehaviorSpec({
        val filter = RequestLoggingFilter()

        Given("Authorization 헤더가 있는 요청이 주어졌을 때") {
            val request =
                MockHttpServletRequest("GET", "/boards").apply {
                    addHeader("Authorization", "Bearer raw-access-token")
                    addHeader("X-API-Version", "1")
                }
            val response = MockHttpServletResponse()

            When("요청 로그를 남기면") {
                val logs =
                    captureRequestLogs {
                        filter.doFilter(request, response) { _, res ->
                            (res as MockHttpServletResponse).status = 200
                        }
                    }

                Then("Authorization 원문은 마스킹하고 일반 헤더는 남긴다") {
                    val message = logs.single()

                    message shouldContain "GET /boards 200"
                    message shouldContain "Authorization=***"
                    message shouldContain "X-API-Version=1"
                    message shouldNotContain "raw-access-token"
                    message shouldNotContain "Bearer raw-access-token"
                }
            }
        }

        Given("소문자 authorization 헤더가 있는 요청이 주어졌을 때") {
            val request =
                MockHttpServletRequest("GET", "/boards").apply {
                    addHeader("authorization", "Bearer raw-access-token")
                }
            val response = MockHttpServletResponse()

            When("요청 로그를 남기면") {
                val logs =
                    captureRequestLogs {
                        filter.doFilter(request, response) { _, _ -> }
                    }

                Then("헤더명 대소문자와 무관하게 마스킹한다") {
                    val message = logs.single()

                    message shouldContain "authorization=***"
                    message shouldNotContain "raw-access-token"
                }
            }
        }

        Given("HttpPayloadAttributes가 민감하다고 규정한 헤더가 모두 담긴 요청이 주어졌을 때") {
            val request =
                MockHttpServletRequest("POST", "/auth/login").apply {
                    addHeader("Authorization", "Bearer $SECRET")
                    addHeader("Proxy-Authorization", "Basic $SECRET")
                    addHeader("Cookie", "session=$SECRET")
                    addHeader("Set-Cookie", "refresh=$SECRET")
                    addHeader("X-API-Key", SECRET)
                    addHeader("X-Refresh-Token", SECRET)
                    addHeader("X-Client-Secret", SECRET)
                    addHeader("X-Csrf-Token", SECRET)
                    addHeader("X-Api-Key-Hint", SECRET)
                    addHeader("X-Request-Id", "req-1")
                }
            val response = MockHttpServletResponse()

            When("요청 로그를 남기면") {
                val message =
                    captureRequestLogs {
                        filter.doFilter(request, response) { _, _ -> }
                    }.single()

                Then("고정 목록에 있는 민감 헤더를 전부 마스킹한다") {
                    message shouldContain "Authorization=***"
                    message shouldContain "Proxy-Authorization=***"
                    message shouldContain "Cookie=***"
                    message shouldContain "Set-Cookie=***"
                    message shouldContain "X-API-Key=***"
                }

                Then("민감 조각이 이름에 들어간 헤더도 마스킹한다") {
                    message shouldContain "X-Refresh-Token=***"
                    message shouldContain "X-Client-Secret=***"
                    message shouldContain "X-Csrf-Token=***"
                    message shouldContain "X-Api-Key-Hint=***"
                }

                Then("어떤 헤더 값도 원문으로 남지 않는다") {
                    message shouldNotContain SECRET
                }

                Then("민감하지 않은 헤더는 값을 그대로 남긴다") {
                    message shouldContain "X-Request-Id=req-1"
                }
            }
        }

        Given("같은 헤더가 여러 번 들어온 요청이 주어졌을 때") {
            val request =
                MockHttpServletRequest("GET", "/boards").apply {
                    addHeader("Accept-Language", "ko")
                    addHeader("Accept-Language", "en")
                }
            val response = MockHttpServletResponse()

            When("요청 로그를 남기면") {
                val message =
                    captureRequestLogs {
                        filter.doFilter(request, response) { _, _ -> }
                    }.single()

                Then("값을 쉼표로 이어 한 항목으로 남긴다") {
                    message shouldContain "Accept-Language=ko,en"
                }
            }
        }

        Given("actuator 요청이 주어졌을 때") {
            val request = MockHttpServletRequest("GET", "/actuator/health")
            val response = MockHttpServletResponse()
            var invoked = false

            When("필터를 통과하면") {
                val logs =
                    captureRequestLogs {
                        filter.doFilter(request, response) { _, _ ->
                            invoked = true
                        }
                    }

                Then("요청 로그를 남기지 않고 다음 필터로 진행한다") {
                    invoked.shouldBeTrue()
                    logs.shouldBeEmpty()
                }
            }
        }
    })

private fun captureRequestLogs(block: () -> Unit): List<String> {
    val logger = LoggerFactory.getLogger(RequestLoggingFilter::class.java) as Logger
    val previousLevel = logger.level
    val appender =
        ListAppender<ILoggingEvent>().apply {
            start()
        }

    logger.level = Level.INFO
    logger.addAppender(appender)
    return try {
        block()
        appender.list.map { it.formattedMessage }
    } finally {
        logger.detachAppender(appender)
        appender.stop()
        logger.level = previousLevel
    }
}
