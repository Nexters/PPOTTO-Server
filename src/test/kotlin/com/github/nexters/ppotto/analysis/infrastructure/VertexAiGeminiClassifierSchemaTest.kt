package com.github.nexters.ppotto.analysis.infrastructure

import com.google.genai.types.Schema
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class VertexAiGeminiClassifierSchemaTest :
    BehaviorSpec({
        Given("Gemini 분류 응답 schema가 주어졌을 때") {
            When("schema를 확인하면") {
                Then("동적 enum 제약을 포함하지 않는다") {
                    VertexAiGeminiClassifier.classificationResponseSchema().containsEnum() shouldBe false
                }
            }
        }

        Given("Gemini 스티커 재생성 응답 schema가 주어졌을 때") {
            When("schema를 확인하면") {
                Then("동적 enum 제약을 포함하지 않는다") {
                    VertexAiGeminiClassifier.stickerResponseSchema().containsEnum() shouldBe false
                }
            }
        }
    })

private fun Schema.containsEnum(): Boolean {
    if (enum_().isPresent) return true
    if (anyOf().map { schemas -> schemas.any { it.containsEnum() } }.orElse(false)) return true
    if (items().map { it.containsEnum() }.orElse(false)) return true
    if (properties().map { properties -> properties.values.any { it.containsEnum() } }.orElse(false)) return true
    return false
}
