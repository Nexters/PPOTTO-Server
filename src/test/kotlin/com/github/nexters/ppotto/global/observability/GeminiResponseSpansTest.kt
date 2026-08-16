package com.github.nexters.ppotto.global.observability

import com.google.genai.types.Candidate
import com.google.genai.types.GenerateContentResponse
import com.google.genai.types.GenerateContentResponseUsageMetadata
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class GeminiResponseSpansTest :
    BehaviorSpec({
        Given("토큰 사용량과 finishReason이 모두 담긴 Gemini 응답이면") {
            val response =
                GenerateContentResponse
                    .builder()
                    .modelVersion("gemini-2.5-flash-002")
                    .responseId("resp-123")
                    .candidates(Candidate.builder().finishReason("STOP"))
                    .usageMetadata(
                        GenerateContentResponseUsageMetadata
                            .builder()
                            .promptTokenCount(50)
                            .toolUsePromptTokenCount(10)
                            .cachedContentTokenCount(40)
                            .candidatesTokenCount(100)
                            .thoughtsTokenCount(30)
                            .totalTokenCount(190),
                    ).build()

            When("스팬에 기록하면") {
                val handle = RecordingLlmSpanHandle()
                handle.record(response)

                Then("입력 토큰은 프롬프트와 도구 프롬프트의 합이다") {
                    handle.inputTokens shouldBe 60
                }

                Then("출력 토큰은 candidates와 reasoning의 합이다") {
                    handle.outputTokens shouldBe 130
                }

                Then("캐시된 입력 토큰과 reasoning 토큰을 따로 기록한다") {
                    handle.cachedInputTokens shouldBe 40
                    handle.reasoningOutputTokens shouldBe 30
                }

                Then("전체 토큰, 응답 모델, 응답 id, finishReason을 기록한다") {
                    handle.totalTokens shouldBe 190
                    handle.recordedResponseModel shouldBe "gemini-2.5-flash-002"
                    handle.recordedResponseId shouldBe "resp-123"
                    handle.recordedFinishReasons shouldBe listOf("STOP")
                }

                Then("프롬프트나 응답 본문은 기록하지 않는다") {
                    handle.attributes shouldNotContainKey "gen_ai.input.messages"
                    handle.attributes shouldNotContainKey "gen_ai.output.messages"
                }
            }
        }

        Given("usageMetadata가 없는 Gemini 응답이면") {
            val response = GenerateContentResponse.builder().build()

            When("스팬에 기록하면") {
                val handle = RecordingLlmSpanHandle()
                handle.record(response)

                Then("토큰과 응답 정보를 남기지 않는다") {
                    handle.usageRecorded shouldBe false
                    handle.recordedResponseModel.shouldBeNull()
                    handle.recordedResponseId.shouldBeNull()
                    handle.recordedFinishReasons.shouldBeNull()
                }
            }
        }

        Given("일부 토큰 항목만 채워진 Gemini 응답이면") {
            val response =
                GenerateContentResponse
                    .builder()
                    .usageMetadata(
                        GenerateContentResponseUsageMetadata
                            .builder()
                            .promptTokenCount(50)
                            .candidatesTokenCount(100),
                    ).build()

            When("스팬에 기록하면") {
                val handle = RecordingLlmSpanHandle()
                handle.record(response)

                Then("없는 항목은 0으로 채우지 않고 null로 둔다") {
                    handle.inputTokens shouldBe 50
                    handle.outputTokens shouldBe 100
                    handle.cachedInputTokens.shouldBeNull()
                    handle.reasoningOutputTokens.shouldBeNull()
                    handle.totalTokens.shouldBeNull()
                }
            }
        }

        Given("여러 candidate가 같은 finishReason을 반환하면") {
            val response =
                GenerateContentResponse
                    .builder()
                    .candidates(
                        Candidate.builder().finishReason("STOP"),
                        Candidate.builder().finishReason("STOP"),
                        Candidate.builder().finishReason("MAX_TOKENS"),
                    ).build()

            When("스팬에 기록하면") {
                val handle = RecordingLlmSpanHandle()
                handle.record(response)

                Then("중복을 제거한 목록을 기록한다") {
                    handle.recordedFinishReasons shouldBe listOf("STOP", "MAX_TOKENS")
                }
            }
        }
    })

private class RecordingLlmSpanHandle : LlmSpanHandle {
    var usageRecorded: Boolean = false
    var inputTokens: Int? = null
    var outputTokens: Int? = null
    var cachedInputTokens: Int? = null
    var reasoningOutputTokens: Int? = null
    var totalTokens: Int? = null
    var recordedResponseModel: String? = null
    var recordedResponseId: String? = null
    var recordedFinishReasons: List<String>? = null
    val attributes: MutableMap<String, Any> = mutableMapOf()

    override fun setUsage(
        inputTokens: Int?,
        outputTokens: Int?,
        cachedInputTokens: Int?,
        reasoningOutputTokens: Int?,
        totalTokens: Int?,
    ) {
        usageRecorded = true
        this.inputTokens = inputTokens
        this.outputTokens = outputTokens
        this.cachedInputTokens = cachedInputTokens
        this.reasoningOutputTokens = reasoningOutputTokens
        this.totalTokens = totalTokens
    }

    override fun setResponseModel(model: String) {
        recordedResponseModel = model
    }

    override fun setResponseId(responseId: String) {
        recordedResponseId = responseId
    }

    override fun setFinishReasons(reasons: List<String>) {
        recordedFinishReasons = reasons
    }

    override fun setAttribute(
        key: String,
        value: String,
    ) {
        attributes[key] = value
    }

    override fun setAttribute(
        key: String,
        value: Long,
    ) {
        attributes[key] = value
    }
}
