package com.github.nexters.ppotto.notification.application

import com.github.nexters.ppotto.notification.domain.DevicePlatform
import com.github.nexters.ppotto.notification.domain.PushNotificationRequestedEvent
import com.github.nexters.ppotto.notification.infrastructure.DeviceTokenRepository
import com.github.nexters.ppotto.notification.support.FakePushNotifier
import com.github.nexters.ppotto.notification.support.SentPush
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.springframework.context.ApplicationEventPublisher

class PushNotificationEventListenerTest(
    eventPublisher: ApplicationEventPublisher,
    deviceTokenRepository: DeviceTokenRepository,
    pushNotifier: FakePushNotifier,
    userRepository: UserRepository,
) : IntegrationTest({
        Given("디바이스 토큰을 등록한 사용자가 있을 때") {
            val user = userRepository.saveTestUser()
            deviceTokenRepository.upsert(user.id, "device-listener", DevicePlatform.IOS, "fcm-listener")

            When("푸시 알림 요청 이벤트를 발행하면") {
                eventPublisher.publishEvent(
                    PushNotificationRequestedEvent(
                        userId = user.id,
                        title = "리캡이 도착했어요",
                        body = "오늘의 스티커를 확인해보세요",
                        data = mapOf("type" to "RECAP"),
                    ),
                )

                Then("이벤트 발행이 끝난 시점에 이미 제목, 본문, 데이터가 그대로 전달돼 있다") {
                    pushNotifier.sentMessages.single() shouldBe
                        SentPush(
                            tokens = listOf("fcm-listener"),
                            title = "리캡이 도착했어요",
                            body = "오늘의 스티커를 확인해보세요",
                            data = mapOf("type" to "RECAP"),
                        )
                }
            }
        }

        Given("디바이스 토큰이 없는 사용자가 있을 때") {
            val user = userRepository.saveTestUser()

            When("푸시 알림 요청 이벤트를 발행하면") {
                eventPublisher.publishEvent(
                    PushNotificationRequestedEvent(user.id, "리캡이 도착했어요", "오늘의 스티커를 확인해보세요"),
                )

                Then("FCM 전송을 시도하지 않고 발행 측도 실패하지 않는다") {
                    pushNotifier.sentMessages.shouldBeEmpty()
                }
            }
        }
    })
