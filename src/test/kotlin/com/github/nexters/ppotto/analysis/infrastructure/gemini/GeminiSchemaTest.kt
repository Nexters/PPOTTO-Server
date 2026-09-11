package com.github.nexters.ppotto.analysis.infrastructure.gemini

import com.github.nexters.ppotto.analysis.infrastructure.gemini.geminiSchema
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

@GeminiObject("샘플 응답")
private data class SampleResponse(
    @GeminiField(description = "첫 번째")
    val first: String,
    @GeminiField(description = "두 번째", required = false)
    val second: String?,
    @GeminiField(description = "세 번째", itemDescription = "원소", minItems = 2, maxItems = 5)
    val third: List<String>,
    @GeminiField(description = "네 번째")
    val fourth: Boolean?,
    @GeminiField(description = "다섯 번째")
    val fifth: SampleNested,
)

@GeminiObject("설명이 빈 응답")
private data class BlankDescriptionResponse(
    @GeminiField(description = "  ")
    val first: String,
)

@GeminiObject("중첩 응답")
private data class SampleNested(
    @GeminiField(description = "좌표")
    val position: Double,
)

private data class UnannotatedResponse(
    val first: String,
)

@GeminiObject("설명 없는 필드를 가진 응답")
private data class PartlyAnnotatedResponse(
    @GeminiField(description = "첫 번째")
    val first: String,
    val second: String,
)

class GeminiSchemaTest :
    BehaviorSpec({
        Given("응답 타입에 스키마 설명을 붙였을 때") {
            val schema = geminiSchema<SampleResponse>()

            When("스키마를 만들면") {
                Then("생성자에 쓴 순서가 그대로 디코딩 순서가 된다") {
                    schema.propertyOrdering().get() shouldContainExactly
                        listOf("first", "second", "third", "fourth", "fifth")
                }

                Then("required를 끄지 않은 필드만 필수가 된다") {
                    schema.required().get() shouldContainExactly listOf("first", "third", "fourth", "fifth")
                }

                Then("nullable 여부가 필수 여부를 좌우하지 않는다") {
                    schema
                        .required()
                        .get()
                        .contains("fourth") shouldBe true
                }

                Then("필드마다 붙인 설명을 갖는다") {
                    schema
                        .properties()
                        .get()["first"]!!
                        .description()
                        .get() shouldBe "첫 번째"
                }

                Then("리스트는 원소 설명과 개수 제약을 함께 갖는다") {
                    val third = schema.properties().get()["third"]!!
                    third
                        .items()
                        .get()
                        .description()
                        .get() shouldBe "원소"
                    third.minItems().get() shouldBe 2L
                    third.maxItems().get() shouldBe 5L
                }

                Then("중첩 응답 타입은 펼쳐지되 필드에 적은 설명이 타입 설명을 이긴다") {
                    val fifth = schema.properties().get()["fifth"]!!
                    fifth.description().get() shouldBe "다섯 번째"
                    fifth.propertyOrdering().get() shouldContainExactly listOf("position")
                }
            }
        }

        Given("스키마 설명을 붙이지 않은 응답 타입이 주어졌을 때") {
            When("스키마를 만들면") {
                val failure = shouldThrow<IllegalStateException> { geminiSchema<UnannotatedResponse>() }

                Then("설명 없는 스키마를 내보내지 않고 타입 이름과 함께 실패한다") {
                    failure.message!! shouldContain "UnannotatedResponse"
                }
            }
        }

        Given("설명을 빈 문자열로 둔 필드가 있을 때") {
            When("스키마를 만들면") {
                val failure = shouldThrow<IllegalArgumentException> { geminiSchema<BlankDescriptionResponse>() }

                Then("설명 없는 필드를 모델에 내보내지 않고 실패한다") {
                    failure.message!! shouldContain "first"
                }
            }
        }

        Given("일부 필드에만 설명을 붙인 응답 타입이 주어졌을 때") {
            When("스키마를 만들면") {
                val failure = shouldThrow<IllegalStateException> { geminiSchema<PartlyAnnotatedResponse>() }

                Then("설명이 빠진 필드를 이름과 함께 알린다") {
                    failure.message!! shouldContain "second"
                }
            }
        }
    })
