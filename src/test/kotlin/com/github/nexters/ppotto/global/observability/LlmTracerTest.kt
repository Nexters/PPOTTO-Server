package com.github.nexters.ppotto.global.observability

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.SpanStatus
import io.sentry.protocol.SentryTransaction
import java.util.concurrent.CopyOnWriteArrayList

class LlmTracerTest :
    BehaviorSpec({
        val captured = CopyOnWriteArrayList<SentryTransaction>()

        beforeSpec {
            Sentry.init { options ->
                options.dsn = TEST_DSN
                options.tracesSampleRate = 1.0
                options.isEnableUncaughtExceptionHandler = false
                options.isEnableBackpressureHandling = false
                options.isEnableAutoSessionTracking = false
                options.beforeSend = SentryOptions.BeforeSendCallback { _, _ -> null }
                options.beforeSendTransaction =
                    SentryOptions.BeforeSendTransactionCallback { transaction, _ ->
                        captured.add(transaction)
                        null
                    }
            }
        }

        afterSpec { Sentry.close() }

        Given("부모 스팬이 없는 비동기 경로에서") {
            When("LLM 호출을 trace로 감싸면") {
                captured.clear()
                val result =
                    LlmTracer.trace(
                        LlmPipeline.PHOTO_CLASSIFICATION,
                        MODEL,
                        attributes = mapOf("ppotto.llm.photo_count" to "3"),
                    ) { span ->
                        span.setUsage(
                            inputTokens = 60,
                            outputTokens = 130,
                            cachedInputTokens = 40,
                            reasoningOutputTokens = 30,
                            totalTokens = 190,
                        )
                        span.setResponseModel("gemini-2.5-flash-002")
                        span.setResponseId("resp-123")
                        span.setFinishReasons(listOf("STOP"))
                        span.setSystemInstructions("너는 사진 분류기다")
                        span.setInputMessages(
                            listOf(
                                LlmMessage(
                                    LlmRole.USER,
                                    listOf(
                                        LlmMessagePart.Uri("gs://bucket/photo-1.jpg", "image/jpeg"),
                                        LlmMessagePart.Text("분류해줘"),
                                    ),
                                ),
                            ),
                        )
                        span.setOutputMessages(
                            listOf(LlmMessage(LlmRole.ASSISTANT, listOf(LlmMessagePart.Text("{\"theme\":\"바다\"}")))),
                        )
                        "ok"
                    }

                Then("블록의 결과를 그대로 돌려준다") {
                    result shouldBe "ok"
                }

                Then("루트 트랜잭션이 gen_ai.chat 규약으로 기록된다") {
                    val trace =
                        captured
                            .single()
                            .also { it.transaction shouldBe "chat $MODEL" }
                            .contexts.trace
                            .shouldNotBeNull()

                    trace.operation shouldBe "gen_ai.chat"
                    trace.status shouldBe SpanStatus.OK
                    trace.data["gen_ai.operation.name"] shouldBe "chat"
                    trace.data["gen_ai.provider.name"] shouldBe "gcp.gemini"
                    trace.data["gen_ai.system"] shouldBe "gcp.gemini"
                    trace.data["gen_ai.request.model"] shouldBe MODEL
                    trace.data["gen_ai.response.model"] shouldBe "gemini-2.5-flash-002"
                    trace.data["gen_ai.response.id"] shouldBe "resp-123"
                    trace.data["gen_ai.pipeline.name"] shouldBe "photo-classification"
                    trace.data["gen_ai.response.finish_reasons"] shouldBe "STOP"
                    trace.data["gen_ai.usage.input_tokens"] shouldBe 60
                    trace.data["gen_ai.usage.output_tokens"] shouldBe 130
                    trace.data["gen_ai.usage.cache_read.input_tokens"] shouldBe 40
                    trace.data["gen_ai.usage.reasoning.output_tokens"] shouldBe 30
                    trace.data["gen_ai.usage.total_tokens"] shouldBe 190
                    trace.data["ppotto.llm.photo_count"] shouldBe "3"
                }

                Then("프롬프트와 응답 본문을 스펙 키와 구버전 키 양쪽에 담는다") {
                    val data =
                        captured
                            .single()
                            .contexts.trace
                            .shouldNotBeNull()
                            .data

                    data["gen_ai.system_instructions"] shouldBe "너는 사진 분류기다"
                    data["gen_ai.input.messages"] shouldBe
                        """[{"role":"user","parts":[{"type":"uri","modality":"image","mime_type":"image/jpeg",""" +
                        """"uri":"gs://bucket/photo-1.jpg"},{"type":"text","content":"분류해줘"}]}]"""
                    data["gen_ai.request.messages"] shouldBe
                        """[{"role":"user","content":[{"type":"uri","modality":"image","mime_type":"image/jpeg",""" +
                        """"uri":"gs://bucket/photo-1.jpg"},{"type":"text","text":"분류해줘"}]}]"""
                    data["gen_ai.output.messages"] shouldBe
                        """[{"role":"assistant","parts":[{"type":"text","content":"{\"theme\":\"바다\"}"}]}]"""
                    data["gen_ai.response.text"] shouldBe """["{\"theme\":\"바다\"}"]"""
                }
            }
        }

        Given("이미 진행 중인 트랜잭션이 있으면") {
            When("LLM 호출을 trace로 감싸면") {
                captured.clear()
                val parent = Sentry.startTransaction("POST /analysis", "http.server")
                parent.makeCurrent().use {
                    LlmTracer.trace(LlmPipeline.STICKER_SUBJECT_VERIFICATION, MODEL) { }
                }
                parent.finish()

                Then("새 트랜잭션 대신 자식 스팬으로 붙는다") {
                    val transaction = captured.single()
                    transaction.transaction shouldBe "POST /analysis"

                    val span = transaction.spans.single()
                    span.op shouldBe "gen_ai.chat"
                    span.description shouldBe "chat $MODEL"
                    span.status shouldBe SpanStatus.OK
                    span.data
                        .shouldNotBeNull()["gen_ai.pipeline.name"] shouldBe "sticker-subject-verification"
                }
            }
        }

        Given("LLM 호출이 실패하면") {
            When("trace 블록에서 예외가 나면") {
                captured.clear()

                Then("예외를 스팬에 남기고 INTERNAL_ERROR로 종료한 뒤 다시 던진다") {
                    shouldThrow<IllegalStateException> {
                        LlmTracer.trace(LlmPipeline.STICKER_REGENERATION, MODEL) {
                            error("gemini 호출 실패")
                        }
                    }

                    captured
                        .single()
                        .contexts.trace
                        .shouldNotBeNull()
                        .status shouldBe SpanStatus.INTERNAL_ERROR
                }
            }
        }
    })

private const val MODEL = "gemini-2.5-flash"
private const val TEST_DSN = "https://public@localhost/1"
