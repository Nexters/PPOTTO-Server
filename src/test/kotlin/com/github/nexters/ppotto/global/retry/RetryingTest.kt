package com.github.nexters.ppotto.global.retry

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.slf4j.LoggerFactory

private const val DESCRIPTION = "외부 호출"

class RetryingTest :
    BehaviorSpec({
        val log = LoggerFactory.getLogger("retrying-test")

        Given("첫 시도에 성공하는 블록이 주어졌을 때") {
            val sleeps = mutableListOf<Long>()
            var attempts = 0

            When("재시도로 감싸 실행하면") {
                val result =
                    retrying(RetryPolicy(5, 100, 2), log, DESCRIPTION, sleeps::add) {
                        attempts += 1
                        "성공"
                    }

                Then("결과를 그대로 담아 돌려준다") {
                    result.getOrNull() shouldBe "성공"
                }

                Then("한 번만 시도하고 대기하지 않는다") {
                    attempts shouldBe 1
                    sleeps.shouldBeEmpty()
                }
            }
        }

        Given("두 번 실패한 뒤 성공하는 블록이 주어졌을 때") {
            val sleeps = mutableListOf<Long>()
            var attempts = 0

            When("최대 5회 정책으로 재시도하면") {
                val result =
                    retrying(RetryPolicy(5, 100, 2), log, DESCRIPTION, sleeps::add) {
                        attempts += 1
                        if (attempts < 3) throw IllegalStateException("일시 실패")
                        "성공"
                    }

                Then("세 번째 시도에서 성공한다") {
                    result.isSuccess.shouldBeTrue()
                    result.getOrNull() shouldBe "성공"
                    attempts shouldBe 3
                }

                Then("대기 시간은 100ms, 200ms 순으로 늘어난다") {
                    sleeps shouldBe listOf(100L, 200L)
                }
            }
        }

        Given("모든 시도가 실패하는 블록이 주어졌을 때") {
            val sleeps = mutableListOf<Long>()
            var attempts = 0

            When("최대 5회 정책으로 재시도하면") {
                val result =
                    retrying(RetryPolicy(5, 100, 2), log, DESCRIPTION, sleeps::add) {
                        attempts += 1
                        throw IllegalStateException("계속 실패")
                    }

                Then("실패한 Result를 돌려준다") {
                    result.isFailure.shouldBeTrue()
                    attempts shouldBe 5
                }

                Then("마지막 시도 뒤에는 대기하지 않는다") {
                    sleeps shouldBe listOf(100L, 200L, 400L, 800L)
                }
            }
        }

        Given("재시도 대상이 아닌 예외를 던지는 블록이 주어졌을 때") {
            val sleeps = mutableListOf<Long>()
            var attempts = 0

            When("retryOn이 false를 돌려주면") {
                val result =
                    retrying(
                        RetryPolicy(5, 100, 2),
                        log,
                        DESCRIPTION,
                        sleeps::add,
                        { false },
                    ) {
                        attempts += 1
                        throw IllegalStateException("재시도 불가")
                    }

                Then("재시도하지 않고 바로 실패를 돌려준다") {
                    result.isFailure.shouldBeTrue()
                    result.isSuccess.shouldBeFalse()
                    attempts shouldBe 1
                    sleeps.shouldBeEmpty()
                }
            }
        }

        Given("잘못된 재시도 정책 값이 주어졌을 때") {
            When("최대 시도 횟수가 1보다 작으면") {
                val exception = shouldThrow<IllegalArgumentException> { RetryPolicy(0) }

                Then("재시도 횟수 오류로 거부한다") {
                    exception.message shouldBe "재시도 횟수는 1 이상이어야 합니다: 0"
                }
            }

            When("대기 시간이 음수이면") {
                val exception = shouldThrow<IllegalArgumentException> { RetryPolicy(3, -1) }

                Then("대기 시간 오류로 거부한다") {
                    exception.message shouldBe "재시도 대기 시간은 0 이상이어야 합니다: -1"
                }
            }

            When("백오프 배수가 1보다 작으면") {
                val exception = shouldThrow<IllegalArgumentException> { RetryPolicy(3, 100, 0) }

                Then("백오프 배수 오류로 거부한다") {
                    exception.message shouldBe "백오프 배수는 1 이상이어야 합니다: 0"
                }
            }
        }
    })
