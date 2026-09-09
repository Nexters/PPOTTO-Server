package com.github.nexters.ppotto.notification.application

import com.github.nexters.ppotto.notification.application.port.PushNotifier
import com.github.nexters.ppotto.notification.application.port.PushSendResult
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

        sendWithRetry(event, tokens)
            .filter { it.invalid }
            .forEach { deviceTokenRepository.deleteByFcmToken(it.token) }
    }

    private fun sendWithRetry(
        event: PushNotificationRequestedEvent,
        tokens: List<String>,
    ): List<PushSendResult> {
        var delayMillis = INITIAL_RETRY_DELAY_MILLIS
        for (attempt in 1..MAX_RETRY_ATTEMPTS) {
            val sent = runCatching { pushNotifier.sendToTokens(tokens, event.title, event.body, event.data) }
            val failure = sent.exceptionOrNull() ?: return sent.getOrThrow()

            if (attempt == MAX_RETRY_ATTEMPTS) {
                log.error("푸시 알림을 {}회 시도했지만 모두 실패했습니다. userId={}", attempt, event.userId, failure)
                break
            }
            log.warn(
                "푸시 알림 {}회차 시도가 실패해 {}ms 뒤에 재시도합니다. userId={}",
                attempt,
                delayMillis,
                event.userId,
                failure,
            )
            sleepMillis(delayMillis)
            delayMillis *= RETRY_BACKOFF_MULTIPLIER
        }
        return emptyList()
    }

    companion object {
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val INITIAL_RETRY_DELAY_MILLIS = 1000L
        private const val RETRY_BACKOFF_MULTIPLIER = 2L

        private val log = LoggerFactory.getLogger(PushNotificationService::class.java)
    }
}
