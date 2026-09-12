package com.github.nexters.ppotto.analysis.infrastructure.gemini

import com.github.nexters.ppotto.analysis.infrastructure.gemini.GeminiResponseSchemas
import com.github.nexters.ppotto.analysis.infrastructure.gemini.geminiPrompt
import com.google.genai.types.MediaResolution
import com.google.genai.types.Schema
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private const val TRACE_TEXT_PART_LIMIT = 16384

class GeminiPromptTest :
    BehaviorSpec({
        val aliases = (1..100).map { "P%03d".format(it) }

        Given("분류 프롬프트가 주어졌을 때") {
            val prompt = themeClassificationPrompt(aliases)

            When("프롬프트를 확인하면") {
                Then("카피 톤 지시를 시스템 지시로 함께 들고 있다") {
                    prompt.systemInstruction.shouldNotBeNull() shouldContain "verdict card"
                }

                Then("톤 앵커가 컷아웃 가이드보다 뒤에 와서 카피 필드 디코딩에 더 가깝다") {
                    val names = prompt.sectionNames
                    names.indexOf("copy style examples") shouldBe names.indexOf("sticker candidate guide") + 1
                }

                Then("분류는 중간 해상도로 사진을 읽는다") {
                    prompt.mediaResolution
                        .shouldNotBeNull()
                        .knownEnum() shouldBe MediaResolution.Known.MEDIA_RESOLUTION_MEDIUM
                }

                Then("사진 100장을 붙여도 트레이스 한 조각 안에 들어간다") {
                    prompt.text.length shouldBeLessThanOrEqual TRACE_TEXT_PART_LIMIT
                }
            }
        }

        Given("컷아웃 대상만 고르는 프롬프트가 주어졌을 때") {
            When("스티커 재생성과 피사체 재확인 프롬프트를 확인하면") {
                Then("카피 톤 지시가 붙지 않는다") {
                    stickerRegenerationPrompt(aliases, "P002").systemInstruction.shouldBeNull()
                    stickerSubjectVerificationPrompt("빨간 우산").systemInstruction.shouldBeNull()
                }

                Then("해상도를 낮추지 않는다 — 컷아웃 대상 판별은 세밀함이 필요하다") {
                    stickerRegenerationPrompt(aliases, "P002").mediaResolution.shouldBeNull()
                    stickerSubjectVerificationPrompt("빨간 우산").mediaResolution.shouldBeNull()
                }
            }
        }

        Given("프롬프트가 응답 필드 이름을 지목할 때") {
            val text = themeClassificationPrompt(aliases).text
            val schemaNames = GeminiResponseSchemas.CLASSIFICATION_RESPONSE_SCHEMA.propertyNames()

            When("지목한 이름을 응답 스키마와 맞춰보면") {
                Then("스키마에 없는 필드를 지시하지 않는다") {
                    val mentioned =
                        listOf(
                            "observedDetails",
                            "theme",
                            "categorizedPhotoIds",
                            "recap.badge",
                            "recap.text",
                            "comments.speechBubbles",
                            "comments.keywordChips",
                            "sticker.sourcePhotoId",
                            "sticker.targetSubject",
                        )
                    mentioned.forEach { path -> text shouldContain path }
                    schemaNames shouldContainAll mentioned.map { it.substringAfterLast('.') }
                }
            }
        }

        Given("섹션을 잘못 조립했을 때") {
            When("같은 이름의 섹션을 두 번 넣으면") {
                val failure =
                    shouldThrow<IllegalArgumentException> {
                        geminiPrompt {
                            section("task", "하나")
                            section("task", "둘")
                        }
                    }

                Then("나중 섹션이 앞 섹션을 조용히 덮지 않고 실패한다") {
                    failure.message!! shouldContain "task"
                }
            }

            When("내용이 빈 섹션을 넣으면") {
                val failure =
                    shouldThrow<IllegalArgumentException> {
                        geminiPrompt {
                            section("task", "하나")
                            section("empty", "   ")
                        }
                    }

                Then("빈 지시를 모델에 보내지 않고 실패한다") {
                    failure.message!! shouldContain "empty"
                }
            }
        }
    })

private fun Schema.propertyNames(): Set<String> {
    val fromItems = items().map { it.propertyNames() }.orElse(emptySet())
    val fromProperties =
        properties()
            .map { properties -> properties.keys + properties.values.flatMap { it.propertyNames() } }
            .orElse(emptySet())
    return fromItems + fromProperties
}
