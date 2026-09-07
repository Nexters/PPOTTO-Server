package com.github.nexters.ppotto.notification.presentation

import com.github.nexters.ppotto.notification.domain.DevicePlatform
import com.github.nexters.ppotto.notification.infrastructure.DeviceTokenRepository
import com.github.nexters.ppotto.notification.presentation.dto.RegisterDeviceTokenRequest
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class DeviceTokenControllerTest(
    private val deviceTokenController: DeviceTokenController,
    private val deviceTokenRepository: DeviceTokenRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        Given("인증된 사용자가") {
            val user = userRepository.saveTestUser()

            When("디바이스 토큰을 등록하면") {
                deviceTokenController.register(
                    user.id,
                    RegisterDeviceTokenRequest(deviceId = "device-1", platform = DevicePlatform.IOS, fcmToken = "fcm-token-1"),
                )

                Then("해당 사용자의 토큰으로 조회된다") {
                    deviceTokenRepository.findFcmTokensByUserId(user.id) shouldBe listOf("fcm-token-1")
                }
            }
        }

        Given("이미 디바이스 토큰이 등록된 사용자가") {
            val user = userRepository.saveTestUser()
            deviceTokenController.register(
                user.id,
                RegisterDeviceTokenRequest(deviceId = "device-1", platform = DevicePlatform.IOS, fcmToken = "fcm-token-old"),
            )

            When("같은 deviceId로 새 토큰을 등록하면") {
                deviceTokenController.register(
                    user.id,
                    RegisterDeviceTokenRequest(deviceId = "device-1", platform = DevicePlatform.IOS, fcmToken = "fcm-token-new"),
                )

                Then("기존 행이 새 토큰으로 갱신된다") {
                    deviceTokenRepository.findFcmTokensByUserId(user.id) shouldBe listOf("fcm-token-new")
                }
            }

            When("토큰을 해제하면") {
                deviceTokenController.unregister(user.id, "device-1")

                Then("더 이상 조회되지 않는다") {
                    deviceTokenRepository.findFcmTokensByUserId(user.id).shouldBeEmpty()
                }
            }
        }
    })
