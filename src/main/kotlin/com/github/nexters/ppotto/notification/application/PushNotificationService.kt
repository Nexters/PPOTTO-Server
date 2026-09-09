package com.github.nexters.ppotto.notification.application

import com.github.nexters.ppotto.global.retry.RetryPolicy
import com.github.nexters.ppotto.global.retry.retrying
import com.github.nexters.ppotto.notification.application.port.PushNotifier
import com.github.nexters.ppotto.notification.domain.PushNotificationRequestedEvent
import com.github.nexters.ppotto.notification.infrastructure.DeviceTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class PushNotificationService(
    private val deviceTokenRepository: DeviceTokenRepository,
    private val pushNotifier: PushNotifier,
    private val sleepMillis: (Long) -> Unit,
) {
    @Autowired
    constructor(
        deviceTokenRepository: DeviceTokenRepository,
        pushNotifier: PushNotifier,
    ) : this(deviceTokenRepository, pushNotifier, { millis -> Thread.sleep(millis) })

    fun send(event: PushNotificationRequestedEvent) {
        val tokens = deviceTokenRepository.findFcmTokensByUserId(event.userId)
        if (tokens.isEmpty()) {
            log.info("등록된 디바이스 토큰이 없어 푸시 알림을 건너뜁니다. userId={}", event.userId)
            return
        }

        retrying(RETRY_POLICY, log, "푸시 알림 userId=${event.userId}", sleep = sleepMillis) {
            pushNotifier.sendToTokens(tokens, event.title, event.body, event.data)
        }.getOrDefault(emptyList())
            .filter { it.invalid }
            .forEach { deviceTokenRepository.deleteByFcmToken(it.token) }
    }

    companion object {
        private val RETRY_POLICY = RetryPolicy(maxAttempts = 5, initialDelayMillis = 1000, backoffMultiplier = 2)

        private val log = LoggerFactory.getLogger(PushNotificationService::class.java)
    }
}
