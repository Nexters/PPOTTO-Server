package com.github.nexters.ppotto.global.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.slf4j.LoggerFactory

private const val LOGGER_NAME = "best-effort-test"
private const val DESCRIPTION = "스티커 이미지 정리"

class BestEffortTest :
    BehaviorSpec({
        val log = LoggerFactory.getLogger(LOGGER_NAME)

        Given("정상적으로 끝나는 블록이 주어졌을 때") {
            When("bestEffort로 감싸 실행하면") {
                var result: Int? = null
                val warnings = captureWarnings { result = bestEffort(log, DESCRIPTION) { 42 } }

                Then("블록의 결과를 그대로 돌려준다") {
                    result shouldBe 42
                }

                Then("경고 로그를 남기지 않는다") {
                    warnings.shouldBeEmpty()
                }
            }
        }

        Given("예외를 던지는 블록이 주어졌을 때") {
            When("bestEffort로 감싸 실행하면") {
                var result: Int? = 0
                val warnings =
                    captureWarnings {
                        result = bestEffort(log, DESCRIPTION) { throw IllegalStateException("정리 실패") }
                    }

                Then("예외를 전파하지 않고 null을 돌려준다") {
                    result shouldBe null
                }

                Then("설명을 담은 WARN 로그를 남긴다") {
                    val warning = warnings.single()

                    warning shouldContain DESCRIPTION
                    warning shouldContain "요청 결과에는 영향을 주지 않습니다"
                }
            }
        }
    })

private fun captureWarnings(block: () -> Unit): List<String> {
    val logger = LoggerFactory.getLogger(LOGGER_NAME) as Logger
    val previousLevel = logger.level
    val appender = ListAppender<ILoggingEvent>().apply { start() }

    logger.level = Level.WARN
    logger.addAppender(appender)
    return try {
        block()
        appender.list
            .filter { it.level == Level.WARN }
            .map { it.formattedMessage }
    } finally {
        logger.detachAppender(appender)
        appender.stop()
        logger.level = previousLevel
    }
}
