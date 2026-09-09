package com.github.nexters.ppotto.notification.presentation

import com.github.nexters.ppotto.jooq.tables.references.USER_DEVICE_TOKENS
import com.github.nexters.ppotto.notification.infrastructure.DeviceTokenRepository
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

private fun registerBody(
    deviceId: String,
    fcmToken: String,
    platform: String = "IOS",
) = """{"deviceId":"$deviceId","platform":"$platform","fcmToken":"$fcmToken"}"""

@AutoConfigureMockMvc
class DeviceTokenControllerTest(
    mockMvc: MockMvc,
    deviceTokenRepository: DeviceTokenRepository,
    userRepository: UserRepository,
    dslContext: DSLContext,
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
                            .content(registerBody("device-1", "fcm-token-1")),
                    )

                Then("빈 성공 응답을 반환한다") {
                    result
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data").doesNotExist())
                }

                Then("해당 사용자의 토큰으로 조회된다") {
                    deviceTokenRepository.findFcmTokensByUserId(user.id) shouldContainExactly listOf("fcm-token-1")
                }
            }

            When("fcmToken 없이 등록을 요청하면") {
                val result =
                    mockMvc.perform(
                        post("/device-tokens")
                            .with(authentication(authentication))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerBody("device-1", "")),
                    )

                Then("COMMON-001 오류를 반환한다") {
                    result
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                }
            }

            When("deviceId를 공백만으로 보내면") {
                val result =
                    mockMvc.perform(
                        post("/device-tokens")
                            .with(authentication(authentication))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerBody("   ", "fcm-token-blank-device")),
                    )

                Then("COMMON-001 오류를 반환하고 행을 저장하지 않는다") {
                    result
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                    deviceTokenRepository.findFcmTokensByUserId(user.id).shouldBeEmpty()
                }
            }

            When("정의되지 않은 플랫폼으로 등록을 요청하면") {
                val result =
                    mockMvc.perform(
                        post("/device-tokens")
                            .with(authentication(authentication))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerBody("device-1", "fcm-token-web", platform = "WEB")),
                    )

                Then("COMMON-001 오류를 반환하고 행을 저장하지 않는다") {
                    result
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                    deviceTokenRepository.findFcmTokensByUserId(user.id).shouldBeEmpty()
                }
            }
        }

        Given("한 사용자가 두 기기의 디바이스 토큰을 등록했을 때") {
            val user = userRepository.saveTestUser()
            val authentication =
                UsernamePasswordAuthenticationToken.authenticated(user.id.value, null, emptyList())
            listOf("device-1" to "fcm-token-1", "device-2" to "fcm-token-2").forEach { (deviceId, fcmToken) ->
                mockMvc
                    .perform(
                        post("/device-tokens")
                            .with(authentication(authentication))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerBody(deviceId, fcmToken)),
                    ).andExpect(status().isOk)
            }

            When("두 기기 등록 직후 토큰을 조회하면") {
                Then("기기마다 한 행씩 남아 두 토큰 모두 조회된다") {
                    dslContext.fetchCount(USER_DEVICE_TOKENS, USER_DEVICE_TOKENS.USER_ID.eq(user.id)) shouldBe 2
                    deviceTokenRepository.findFcmTokensByUserId(user.id) shouldContainExactlyInAnyOrder
                        listOf("fcm-token-1", "fcm-token-2")
                }
            }

            When("한 기기의 FCM 토큰만 회전시키면") {
                mockMvc
                    .perform(
                        post("/device-tokens")
                            .with(authentication(authentication))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerBody("device-1", "fcm-token-1-rotated")),
                    ).andExpect(status().isOk)

                Then("그 기기 행만 갱신되고 다른 기기 토큰은 그대로 남는다") {
                    dslContext.fetchCount(USER_DEVICE_TOKENS, USER_DEVICE_TOKENS.USER_ID.eq(user.id)) shouldBe 2
                    deviceTokenRepository.findFcmTokensByUserId(user.id) shouldContainExactlyInAnyOrder
                        listOf("fcm-token-1-rotated", "fcm-token-2")
                }
            }

            When("한 기기의 토큰을 해제하면") {
                mockMvc
                    .perform(
                        delete("/device-tokens")
                            .with(authentication(authentication))
                            .param("deviceId", "device-1"),
                    ).andExpect(status().isOk)

                Then("그 기기 행만 사라지고 다른 기기 토큰은 남는다") {
                    deviceTokenRepository.findFcmTokensByUserId(user.id) shouldContainExactly listOf("fcm-token-2")
                }
            }

            When("등록한 적 없는 deviceId로 해제를 요청하면") {
                val result =
                    mockMvc.perform(
                        delete("/device-tokens")
                            .with(authentication(authentication))
                            .param("deviceId", "device-unknown"),
                    )

                Then("빈 성공 응답을 반환하고 기존 토큰을 지우지 않는다") {
                    result
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data").doesNotExist())
                    deviceTokenRepository.findFcmTokensByUserId(user.id) shouldContainExactlyInAnyOrder
                        listOf("fcm-token-1", "fcm-token-2")
                }
            }
        }

        Given("인증되지 않은 요청일 때") {
            When("디바이스 토큰을 등록하면") {
                val result =
                    mockMvc.perform(
                        post("/device-tokens")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerBody("device-1", "fcm-token-anonymous")),
                    )

                Then("COMMON-004 오류를 반환한다") {
                    result
                        .andExpect(status().isUnauthorized)
                        .andExpect(jsonPath("$.error.code").value("COMMON-004"))
                }
            }
        }
    })
