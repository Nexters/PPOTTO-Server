package com.github.nexters.ppotto.notification.domain

interface PushNotifier {
    fun sendToTokens(
        tokens: List<String>,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap(),
    ): List<PushSendResult>
}

data class PushSendResult(
    val token: String,
    val success: Boolean,
    val invalid: Boolean,
)
