package com.github.nexters.ppotto.global.config

import com.github.nexters.ppotto.support.IntegrationTest
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.cors.CorsConfiguration

private const val TEST_ALLOWED_ORIGIN = "http://localhost:3000"

private fun configurationFor(allowedOrigins: List<String>): CorsConfiguration? =
    CorsConfig()
        .corsConfigurationSource(CorsProperties(allowedOrigins))
        .getCorsConfiguration(MockHttpServletRequest("OPTIONS", "/terms"))

@AutoConfigureMockMvc
class CorsConfigurationSourceTest(
    mockMvc: MockMvc,
) : IntegrationTest({
        Given("Dev처럼 CORS_ALLOWED_ORIGINS가 *일 때") {
            val configuration = configurationFor(listOf("*"))

            When("임의의 origin을 검사하면") {
                val allowed = configuration?.checkOrigin("https://any.example.com")
                val localhost = configuration?.checkOrigin("http://localhost:3000")

                Then("해당 origin을 그대로 반환한다") {
                    allowed shouldBe "https://any.example.com"
                    localhost shouldBe "http://localhost:3000"
                }
            }

            When("허용 정책을 확인하면") {
                Then("Bearer 인증이라 credentials는 허용하지 않는다") {
                    configuration shouldNotBe null
                    configuration?.allowCredentials.shouldBeNull()
                }

                Then("모든 메서드와 헤더를 허용하고 프리플라이트를 1시간 캐시한다") {
                    configuration?.allowedMethods shouldBe listOf("*")
                    configuration?.allowedHeaders shouldBe listOf("*")
                    configuration?.maxAge shouldBe 3600L
                }
            }
        }

        Given("Production처럼 정확한 origin 목록이 주어졌을 때") {
            val configuration = configurationFor(listOf("https://ppotto.co.kr"))

            When("목록에 있는 origin을 검사하면") {
                val checked = configuration?.checkOrigin("https://ppotto.co.kr")

                Then("허용한다") {
                    checked shouldBe "https://ppotto.co.kr"
                }
            }

            When("목록에 없는 origin을 검사하면") {
                val checked = configuration?.checkOrigin("https://evil.example.com")

                Then("거부한다") {
                    checked.shouldBeNull()
                }
            }
        }

        Given("브라우저가 실제 요청 전에 프리플라이트를 보낼 때") {
            When("허용된 origin으로 OPTIONS 프리플라이트를 보내면") {
                val result =
                    mockMvc.perform(
                        options("/terms")
                            .header(HttpHeaders.ORIGIN, TEST_ALLOWED_ORIGIN)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"),
                    )

                Then("Access-Control-Allow-Origin으로 그 origin을 그대로 돌려준다") {
                    result
                        .andExpect(status().isOk)
                        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, TEST_ALLOWED_ORIGIN))
                }

                Then("쿠키를 쓰지 않으므로 Access-Control-Allow-Credentials는 내려주지 않는다") {
                    result.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
                }
            }

            When("허용하지 않은 origin으로 OPTIONS 프리플라이트를 보내면") {
                val result =
                    mockMvc.perform(
                        options("/terms")
                            .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"),
                    )

                Then("403으로 끊고 Access-Control-Allow-Origin을 내려주지 않는다") {
                    result
                        .andExpect(status().isForbidden)
                        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                }
            }
        }
    })
