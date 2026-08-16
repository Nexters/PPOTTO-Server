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
