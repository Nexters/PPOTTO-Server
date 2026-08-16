package com.github.nexters.ppotto.global.observability

import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class GeminiRequestSpansTest :
    BehaviorSpec({
        Given("사진 URI와 프롬프트로 만든 요청이면") {
            val content =
                Content.fromParts(
                    Part.fromUri("gs://ppotto-photos/user/photo-1.jpg", "image/jpeg"),
                    Part.fromUri("gs://ppotto-photos/user/photo-2.webp", "image/webp"),
                    Part.fromText("사진을 테마로 분류해줘"),
                )

            When("스팬에 기록하면") {
                val handle = RecordingLlmSpanHandle()
                handle.recordRequest(content, null)

                Then("user 역할 메시지에 프롬프트 본문과 사진 URI를 모두 담는다") {
                    handle.recordedInputMessages shouldBe
                        listOf(
                            LlmMessage(
                                role = LlmRole.USER,
                                parts =
                                    listOf(
                                        LlmMessagePart.Uri("gs://ppotto-photos/user/photo-1.jpg", "image/jpeg"),
                                        LlmMessagePart.Uri("gs://ppotto-photos/user/photo-2.webp", "image/webp"),
                                        LlmMessagePart.Text("사진을 테마로 분류해줘"),
                                    ),
                            ),
                        )
                }

                Then("systemInstruction이 없으면 남기지 않는다") {
                    handle.recordedSystemInstructions.shouldBeNull()
                }
            }
        }

        Given("systemInstruction이 설정된 요청이면") {
            val content = Content.fromParts(Part.fromText("분류해줘"))
            val config =
                GenerateContentConfig
                    .builder()
                    .systemInstruction(Content.fromParts(Part.fromText("너는 사진 분류기다")))
                    .build()

            When("스팬에 기록하면") {
                val handle = RecordingLlmSpanHandle()
                handle.recordRequest(content, config)

                Then("systemInstruction 텍스트를 따로 남긴다") {
                    handle.recordedSystemInstructions shouldBe "너는 사진 분류기다"
                }
            }
        }

        Given("본문이 없는 요청이면") {
            val content = Content.builder().build()

            When("스팬에 기록하면") {
                val handle = RecordingLlmSpanHandle()
                handle.recordRequest(content, null)

                Then("입력 메시지를 남기지 않는다") {
                    handle.recordedInputMessages.shouldBeNull()
                }
            }
        }
    })
