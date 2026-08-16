package com.github.nexters.ppotto.global.observability

import com.github.nexters.ppotto.support.IntegrationTest
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.sentry.SentryOptions
import io.sentry.spring7.SentryUserProvider
import io.sentry.spring7.tracing.SentryTracingFilter
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.ApplicationContext

class SentryAutoConfigurationWiringTest(
    applicationContext: ApplicationContext,
) : IntegrationTest({
        Given("Sentry 자동 설정이 적용된 컨텍스트에서") {
            When("Sentry 빈을 조회하면") {
                Then("요청 트랜잭션 필터가 등록되어 있다") {
                    applicationContext
                        .getBeansOfType(FilterRegistrationBean::class.java)
                        .values
                        .map { it.filter }
                        .filterIsInstance<SentryTracingFilter>()
                        .size shouldBe 1
                }

                Then("직접 등록한 유저 컨텍스트 제공자를 사용한다") {
                    applicationContext
                        .getBeansOfType(SentryUserProvider::class.java)
                        .values
                        .map { it::class } shouldContain SentryUserContextProvider::class
                }

                Then("직접 등록한 트레이스 샘플러가 유일한 콜백이다") {
                    applicationContext
                        .getBeansOfType(SentryOptions.TracesSamplerCallback::class.java)
                        .values
                        .map { it::class } shouldBe listOf(SentryTracesSampler::class)
                }
            }
        }
    })
