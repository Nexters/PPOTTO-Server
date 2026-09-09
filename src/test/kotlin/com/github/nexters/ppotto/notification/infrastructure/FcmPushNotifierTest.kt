package com.github.nexters.ppotto.notification.infrastructure

import com.google.firebase.messaging.MessagingErrorCode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class FcmPushNotifierTest :
    BehaviorSpec({
        Given("FCM이 UNREGISTERED를 반환했을 때") {
            When("토큰 무효 여부를 판정하면") {
                Then("등록이 해제된 토큰이므로 무효로 표시한다") {
                    MessagingErrorCode.UNREGISTERED.marksTokenInvalid() shouldBe true
                }
            }
        }

        Given("FCM이 SENDER_ID_MISMATCH를 반환했을 때") {
            When("토큰 무효 여부를 판정하면") {
                Then("다른 발신자의 토큰이므로 무효로 표시한다") {
                    MessagingErrorCode.SENDER_ID_MISMATCH.marksTokenInvalid() shouldBe true
                }
            }
        }

        Given("FCM이 INVALID_ARGUMENT를 반환했을 때") {
            When("토큰 무효 여부를 판정하면") {
                Then("토큰이 아니라 메시지 문제일 수 있으므로 무효로 표시하지 않는다") {
                    MessagingErrorCode.INVALID_ARGUMENT.marksTokenInvalid() shouldBe false
                }
            }
        }

        Given("전송이 성공해 오류 코드가 없을 때") {
            val noErrorCode: MessagingErrorCode? = null

            When("토큰 무효 여부를 판정하면") {
                Then("무효로 표시하지 않는다") {
                    noErrorCode.marksTokenInvalid() shouldBe false
                }
            }
        }
    })
