package com.github.nexters.ppotto.notification.infrastructure

import com.github.nexters.ppotto.notification.domain.PushNotifier
import com.github.nexters.ppotto.notification.domain.PushSendResult
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("test")
class NoOpPushNotifier : PushNotifier {
    override fun sendToTokens(
        tokens: List<String>,
        title: String,
        body: String,
        data: Map<String, String>,
    ): List<PushSendResult> {
        log.info("push notification skipped in test profile: tokenCount={}, title={}", tokens.size, title)
        return tokens.map { PushSendResult(token = it, success = true, invalid = false) }
    }

    companion object {
        private val log = LoggerFactory.getLogger(NoOpPushNotifier::class.java)
    }
}
