package com.github.nexters.ppotto.global.observability

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.sentry.CustomSamplingContext
import io.sentry.SamplingContext
import io.sentry.TransactionContext
import org.springframework.mock.web.MockHttpServletRequest

class SentryTracesSamplerTest :
    BehaviorSpec({
        val sampler = SentryTracesSampler()

        Given("헬스체크 요청이면") {
            val samplingContext = samplingContextOf(MockHttpServletRequest("GET", "/actuator/health"))

            When("샘플링 비율을 결정하면") {
                Then("0.0으로 트랜잭션을 버린다") {
                    sampler.sample(samplingContext) shouldBe 0.0
                }
            }
        }

        Given("actuator 하위 요청이면") {
            val samplingContext = samplingContextOf(MockHttpServletRequest("GET", "/actuator/health/readiness"))

            When("샘플링 비율을 결정하면") {
                Then("0.0으로 트랜잭션을 버린다") {
                    sampler.sample(samplingContext) shouldBe 0.0
                }
            }
        }

        Given("일반 API 요청이면") {
            val samplingContext = samplingContextOf(MockHttpServletRequest("GET", "/boards"))

            When("샘플링 비율을 결정하면") {
                Then("null을 반환해 설정된 비율을 따르게 한다") {
                    sampler.sample(samplingContext).shouldBeNull()
                }
            }
        }

        Given("HTTP 요청이 없는 트랜잭션이면") {
            val samplingContext = samplingContextOf(TransactionContext("scheduled-job", "task"), CustomSamplingContext())

            When("샘플링 비율을 결정하면") {
                Then("null을 반환해 설정된 비율을 따르게 한다") {
                    sampler.sample(samplingContext).shouldBeNull()
                }
            }
        }
    })

private fun samplingContextOf(request: MockHttpServletRequest): SamplingContext =
    samplingContextOf(
        TransactionContext("${request.method} ${request.requestURI}", "http.server"),
        CustomSamplingContext().apply { set("request", request) },
    )

private fun samplingContextOf(
    transactionContext: TransactionContext,
    customSamplingContext: CustomSamplingContext,
): SamplingContext = SamplingContext(transactionContext, customSamplingContext, SAMPLE_RAND, emptyMap())

private const val SAMPLE_RAND = 0.5
