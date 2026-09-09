package com.github.nexters.ppotto.notification.presentation

import com.github.nexters.ppotto.notification.infrastructure.DeviceTokenRepository
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

private fun registerBody(fcmToken: String) = """{"deviceId":"device-1","platform":"IOS","fcmToken":"$fcmToken"}"""

@AutoConfigureMockMvc
class DeviceTokenControllerTest(
    mockMvc: MockMvc,
    deviceTokenRepository: DeviceTokenRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        Given("인증된 사용자가") {
            val user = userRepository.saveTestUser()
            val authentication =
                UsernamePasswordAuthenticationToken.authenticated(user.id.value, null, emptyList())

            When("디바이스 토큰을 등록하면") {
                val result =
                    mockMvc.perform(
                        post("/device-tokens")
                            .with(authentication(authentication))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerBody("fcm-token-1")),
                    )

                Then("빈 성공 응답을 반환한다") {
                    result
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data").doesNotExist())
                }

                Then("해당 사용자의 토큰으로 조회된다") {
                    deviceTokenRepository.findFcmTokensByUserId(user.id) shouldBe listOf("fcm-token-1")
                }
            }

            When("fcmToken 없이 등록을 요청하면") {
                val result =
                    mockMvc.perform(
                        post("/device-tokens")
                            .with(authentication(authentication))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"deviceId":"device-1","platform":"IOS","fcmToken":""}"""),
                    )

                Then("COMMON-001 오류를 반환한다") {
                    result
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                }
            }
        }

        Given("이미 디바이스 토큰이 등록된 사용자가") {
            val user = userRepository.saveTestUser()
            val authentication =
                UsernamePasswordAuthenticationToken.authenticated(user.id.value, null, emptyList())
            mockMvc.perform(
                post("/device-tokens")
                    .with(authentication(authentication))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerBody("fcm-token-old")),
            )

            When("같은 deviceId로 새 토큰을 등록하면") {
                mockMvc
                    .perform(
                        post("/device-tokens")
                            .with(authentication(authentication))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerBody("fcm-token-new")),
                    ).andExpect(status().isOk)

                Then("기존 행이 새 토큰으로 갱신된다") {
                    deviceTokenRepository.findFcmTokensByUserId(user.id) shouldBe listOf("fcm-token-new")
                }
            }

            When("토큰을 해제하면") {
                mockMvc
                    .perform(
                        delete("/device-tokens")
                            .with(authentication(authentication))
                            .param("deviceId", "device-1"),
                    ).andExpect(status().isOk)

                Then("더 이상 조회되지 않는다") {
                    deviceTokenRepository.findFcmTokensByUserId(user.id).shouldBeEmpty()
                }
            }
        }

        Given("인증되지 않은 요청일 때") {
            When("디바이스 토큰을 등록하면") {
                val result =
                    mockMvc.perform(
                        post("/device-tokens")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerBody("fcm-token-anonymous")),
                    )

                Then("COMMON-004 오류를 반환한다") {
                    result
                        .andExpect(status().isUnauthorized)
                        .andExpect(jsonPath("$.error.code").value("COMMON-004"))
                }
            }
        }
    })
