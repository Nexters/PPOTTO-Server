package com.github.nexters.ppotto.notification.support

import com.github.nexters.ppotto.notification.domain.PushNotifier
import com.github.nexters.ppotto.notification.domain.PushSendResult
import java.util.concurrent.CopyOnWriteArrayList

data class SentPush(
    val tokens: List<String>,
    val title: String,
    val body: String,
    val data: Map<String, String>,
)

class FakePushNotifier : PushNotifier {
    val sentMessages = CopyOnWriteArrayList<SentPush>()

    override fun sendToTokens(
        tokens: List<String>,
        title: String,
        body: String,
        data: Map<String, String>,
    ): List<PushSendResult> {
        sentMessages += SentPush(tokens, title, body, data)
        return tokens.map { PushSendResult(token = it, success = true, invalid = false) }
    }
}
