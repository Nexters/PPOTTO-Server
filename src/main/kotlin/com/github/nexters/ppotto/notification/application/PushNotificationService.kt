package com.github.nexters.ppotto.notification.application

import com.github.nexters.ppotto.notification.domain.PushNotificationRequestedEvent
import com.github.nexters.ppotto.notification.domain.PushNotifier
import com.github.nexters.ppotto.notification.domain.PushSendResult
import com.github.nexters.ppotto.notification.infrastructure.DeviceTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PushNotificationService(
    private val deviceTokenRepository: DeviceTokenRepository,
    private val pushNotifier: PushNotifier,
) {
    fun send(event: PushNotificationRequestedEvent) {
        val tokens = deviceTokenRepository.findFcmTokensByUserId(event.userId)
        if (tokens.isEmpty()) {
            log.info("push notification skipped, no device tokens: userId={}", event.userId)
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
        repeat(MAX_RETRY_ATTEMPTS) { attemptIndex ->
            val attempt = attemptIndex + 1
            val result = runCatching { pushNotifier.sendToTokens(tokens, event.title, event.body, event.data) }
            result.onSuccess { return it }
            result.onFailure { throwable ->
                if (attempt == MAX_RETRY_ATTEMPTS) {
                    log.error(
                        "push notification failed after {} attempts: userId={}",
                        attempt,
                        event.userId,
                        throwable,
                    )
                } else {
                    log.warn(
                        "push notification attempt {} failed, retrying in {}ms: userId={}",
                        attempt,
                        delayMillis,
                        event.userId,
                        throwable,
                    )
                    Thread.sleep(delayMillis)
                    delayMillis *= RETRY_BACKOFF_MULTIPLIER
                }
            }
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
