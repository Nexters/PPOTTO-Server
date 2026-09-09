package com.github.nexters.ppotto.notification.support

import com.github.nexters.ppotto.notification.application.port.PushNotifier
import com.github.nexters.ppotto.notification.application.port.PushSendResult
import com.github.nexters.ppotto.support.ResettableFake
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

data class SentPush(
    val tokens: List<String>,
    val title: String,
    val body: String,
    val data: Map<String, String>,
)

class FakePushNotifier :
    PushNotifier,
    ResettableFake {
    val sentMessages = CopyOnWriteArrayList<SentPush>()

    val invalidTokens: MutableSet<String> = ConcurrentHashMap.newKeySet()

    val failedTokens: MutableSet<String> = ConcurrentHashMap.newKeySet()

    var failure: Throwable? = null

    override fun sendToTokens(
        tokens: List<String>,
        title: String,
        body: String,
        data: Map<String, String>,
    ): List<PushSendResult> {
        failure?.let { throw it }
        sentMessages += SentPush(tokens, title, body, data)
        return tokens.map {
            PushSendResult(token = it, success = it !in failedTokens && it !in invalidTokens, invalid = it in invalidTokens)
        }
    }

    override fun reset() {
        sentMessages.clear()
        invalidTokens.clear()
        failedTokens.clear()
        failure = null
    }
}
