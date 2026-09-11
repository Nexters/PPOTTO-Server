package com.github.nexters.ppotto.analysis.infrastructure

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private data class SampleResponse(
    val first: String,
    val second: String,
    val third: String?,
)

class GeminiSchemaDslTest :
    BehaviorSpec({
        Given("응답 타입의 모든 필드를 선언한 스키마가 주어졌을 때") {
            val schema =
                objectSchema<SampleResponse>("샘플") {
                    string(SampleResponse::second, "두 번째")
                    string(SampleResponse::first, "첫 번째")
                    string(SampleResponse::third, "세 번째", required = false)
                }

            When("스키마를 확인하면") {
                Then("선언한 순서가 그대로 디코딩 순서가 된다") {
                    schema.propertyOrdering().get() shouldContainExactly listOf("second", "first", "third")
                }

                Then("required를 끄지 않은 필드만 필수가 된다") {
                    schema.required().get() shouldContainExactly listOf("second", "first")
                }

                Then("필드마다 선언한 설명을 갖는다") {
                    schema
                        .properties()
                        .get()["first"]!!
                        .description()
                        .get() shouldBe "첫 번째"
                }
            }
        }

        Given("응답 타입의 필드를 빠뜨린 스키마 선언이 주어졌을 때") {
            When("스키마를 만들면") {
                val failure =
                    shouldThrow<IllegalArgumentException> {
                        objectSchema<SampleResponse>("샘플") {
                            string(SampleResponse::first, "첫 번째")
                            string(SampleResponse::second, "두 번째")
                        }
                    }

                Then("항상 null로 파싱될 필드를 이름과 함께 알린다") {
                    failure.message!! shouldContain "third"
                }
            }
        }

        Given("같은 필드를 두 번 선언한 스키마 선언이 주어졌을 때") {
            When("스키마를 만들면") {
                val failure =
                    shouldThrow<IllegalArgumentException> {
                        objectSchema<SampleResponse>("샘플") {
                            string(SampleResponse::first, "첫 번째")
                            string(SampleResponse::first, "덮어쓰기")
                            string(SampleResponse::second, "두 번째")
                            string(SampleResponse::third, "세 번째")
                        }
                    }

                Then("나중 선언이 앞 선언을 조용히 덮지 않고 실패한다") {
                    failure.message!! shouldContain "first"
                }
            }
        }
    })
