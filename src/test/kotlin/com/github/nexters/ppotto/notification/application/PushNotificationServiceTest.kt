package com.github.nexters.ppotto.notification.application

import com.github.nexters.ppotto.notification.application.port.PushNotifier
import com.github.nexters.ppotto.notification.application.port.PushSendResult
import com.github.nexters.ppotto.notification.domain.DevicePlatform
import com.github.nexters.ppotto.notification.domain.PushNotificationRequestedEvent
import com.github.nexters.ppotto.notification.infrastructure.DeviceTokenRepository
import com.github.nexters.ppotto.notification.support.FakePushNotifier
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

private class AlwaysFailingPushNotifier : PushNotifier {
    val attempts = AtomicInteger()

    override fun sendToTokens(
        tokens: List<String>,
        title: String,
        body: String,
        data: Map<String, String>,
    ): List<PushSendResult> {
        attempts.incrementAndGet()
        error("FCM 전송에 실패했습니다.")
    }
}

class PushNotificationServiceTest(
    pushNotificationService: PushNotificationService,
    deviceTokenRepository: DeviceTokenRepository,
    pushNotifier: FakePushNotifier,
    userRepository: UserRepository,
) : IntegrationTest({
        Given("디바이스 토큰을 가진 사용자에게 FCM 전송이 계속 실패할 때") {
            val user = userRepository.saveTestUser()
            deviceTokenRepository.upsert(user.id, "device-retry", DevicePlatform.IOS, "fcm-retry")
            val failingNotifier = AlwaysFailingPushNotifier()
            val sleptDelays = CopyOnWriteArrayList<Long>()
            val retryingService =
                PushNotificationService(deviceTokenRepository, failingNotifier) { sleptDelays += it }

            When("푸시 알림을 발송하면") {
                shouldNotThrowAny {
                    retryingService.send(PushNotificationRequestedEvent(user.id, "제목", "본문"))
                }

                Then("총 5회까지 시도한다") {
                    failingNotifier.attempts.get() shouldBe 5
                }

                Then("재시도 간격을 1초에서 두 배씩 늘린다") {
                    sleptDelays shouldContainExactly listOf(1000L, 2000L, 4000L, 8000L)
                }

                Then("포기해도 발행 측에 예외를 던지지 않고 토큰을 지우지 않는다") {
                    deviceTokenRepository.findFcmTokensByUserId(user.id) shouldContainExactly listOf("fcm-retry")
                }
            }
        }

        Given("유효한 토큰과 만료된 토큰과 일시 실패한 토큰을 가진 사용자가 있을 때") {
            val user = userRepository.saveTestUser()
            deviceTokenRepository.upsert(user.id, "device-valid", DevicePlatform.IOS, "fcm-valid")
            deviceTokenRepository.upsert(user.id, "device-invalid", DevicePlatform.ANDROID, "fcm-invalid")
            deviceTokenRepository.upsert(user.id, "device-failed", DevicePlatform.ANDROID, "fcm-failed")
            pushNotifier.invalidTokens += "fcm-invalid"
            pushNotifier.failedTokens += "fcm-failed"

            When("푸시 알림을 발송하면") {
                pushNotificationService.send(PushNotificationRequestedEvent(user.id, "제목", "본문"))

                Then("등록된 세 토큰에 한 번만 전송한다") {
                    pushNotifier.sentMessages
                        .single()
                        .tokens shouldContainExactlyInAnyOrder listOf("fcm-valid", "fcm-invalid", "fcm-failed")
                }

                Then("무효 토큰 행만 지우고 실패한 토큰은 남긴다") {
                    deviceTokenRepository.findFcmTokensByUserId(user.id) shouldContainExactlyInAnyOrder
                        listOf("fcm-valid", "fcm-failed")
                }
            }
        }

        Given("디바이스 토큰을 등록하지 않은 사용자가 있을 때") {
            val user = userRepository.saveTestUser()

            When("푸시 알림을 발송하면") {
                pushNotificationService.send(PushNotificationRequestedEvent(user.id, "제목", "본문"))

                Then("FCM 전송을 시도하지 않는다") {
                    pushNotifier.sentMessages.shouldBeEmpty()
                }
            }
        }
    })
