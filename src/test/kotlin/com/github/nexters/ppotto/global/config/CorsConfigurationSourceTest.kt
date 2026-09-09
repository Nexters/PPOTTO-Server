package com.github.nexters.ppotto.global.config

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.cors.CorsConfiguration

class CorsConfigurationSourceTest :
    BehaviorSpec({
        val corsConfig = CorsConfig()

        fun configurationFor(allowedOrigins: List<String>): CorsConfiguration? =
            corsConfig
                .corsConfigurationSource(CorsProperties(allowedOrigins))
                .getCorsConfiguration(MockHttpServletRequest("OPTIONS", "/terms"))

        Given("Dev처럼 CORS_ALLOWED_ORIGINS가 *일 때") {
            val configuration = configurationFor(listOf("*"))

            When("임의의 origin을 검사하면") {
                Then("해당 origin을 그대로 반환한다") {
                    configuration?.checkOrigin("https://any.example.com") shouldBe "https://any.example.com"
                    configuration?.checkOrigin("http://localhost:3000") shouldBe "http://localhost:3000"
                }

                Then("Bearer 인증이라 credentials는 허용하지 않는다") {
                    configuration shouldNotBe null
                    configuration?.allowCredentials.shouldBeNull()
                }
            }
        }

        Given("Production처럼 정확한 origin 목록이 주어졌을 때") {
            val configuration = configurationFor(listOf("https://ppotto.co.kr"))

            When("목록에 있는 origin을 검사하면") {
                Then("허용한다") {
                    configuration?.checkOrigin("https://ppotto.co.kr") shouldBe "https://ppotto.co.kr"
                }
            }

            When("목록에 없는 origin을 검사하면") {
                Then("거부한다") {
                    configuration?.checkOrigin("https://evil.example.com").shouldBeNull()
                }
            }
        }
    })
