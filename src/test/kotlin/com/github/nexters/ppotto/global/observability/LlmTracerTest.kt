package com.github.nexters.ppotto.global.observability

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor

class LlmTracerTest :
    BehaviorSpec({
        val exporter = InMemorySpanExporter.create()

        beforeSpec {
            GlobalOpenTelemetry.resetForTest()
            OpenTelemetrySdk
                .builder()
                .setTracerProvider(
                    SdkTracerProvider
                        .builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build(),
                ).buildAndRegisterGlobal()
        }

        afterSpec { GlobalOpenTelemetry.resetForTest() }

        beforeTest { exporter.reset() }

        Given("LLM 호출을 trace로 감쌌을 때") {
            When("블록이 정상적으로 완료되면") {
                Then("GenAI 속성과 OK 상태를 가진 스팬이 기록된다") {
                    val result =
                        LlmTracer.trace(
                            LlmOperation.CLASSIFY,
                            "gemini-2.5-flash",
                            attributes = mapOf("ppotto.llm.photo_count" to "3"),
                        ) { span ->
                            span.setUsage(inputTokens = 10, outputTokens = 20, totalTokens = 30)
                            span.setFinishReasons(listOf("STOP"))
                            "ok"
                        }

                    result shouldBe "ok"
                    val span = exporter.finishedSpanItems.single()
                    span.name shouldBe "classify gemini-2.5-flash"
                    span.status.statusCode shouldBe StatusCode.OK
                    span.attributes.get(AttributeKey.stringKey("gen_ai.system")) shouldBe "gcp.vertex_ai"
                    span.attributes.get(AttributeKey.stringKey("gen_ai.operation.name")) shouldBe "classify"
                    span.attributes.get(AttributeKey.stringKey("gen_ai.request.model")) shouldBe "gemini-2.5-flash"
                    span.attributes.get(AttributeKey.stringKey("ppotto.llm.photo_count")) shouldBe "3"
                    span.attributes.get(AttributeKey.longKey("gen_ai.usage.input_tokens")) shouldBe 10L
                    span.attributes.get(AttributeKey.longKey("gen_ai.usage.output_tokens")) shouldBe 20L
                    span.attributes.get(AttributeKey.longKey("gen_ai.usage.total_tokens")) shouldBe 30L
                    span.attributes.get(AttributeKey.stringArrayKey("gen_ai.response.finish_reasons")) shouldBe listOf("STOP")
                }
            }

            When("블록에서 예외가 발생하면") {
                Then("예외를 기록하고 ERROR 상태로 스팬을 종료한 뒤 예외를 다시 던진다") {
                    shouldThrow<IllegalStateException> {
                        LlmTracer.trace(LlmOperation.SUBJECT_VERIFICATION, "gemini-2.5-flash") {
                            error("boom")
                        }
                    }

                    val span = exporter.finishedSpanItems.single()
                    span.status.statusCode shouldBe StatusCode.ERROR
                    span.events.any { it.name == "exception" } shouldBe true
                }
            }

            When("블록 안에서 현재 스팬을 조회하면") {
                Then("trace가 만든 스팬이 현재 컨텍스트에 바인딩되어 있다") {
                    LlmTracer.trace(LlmOperation.CLASSIFY, "gemini-2.5-flash") {
                        Span
                            .current()
                            .spanContext.isValid shouldBe true
                    }
                }
            }
        }
    })
