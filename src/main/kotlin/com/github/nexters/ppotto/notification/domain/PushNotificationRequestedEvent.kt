package com.github.nexters.ppotto.notification.domain

import com.github.nexters.ppotto.global.identifier.UserId

data class PushNotificationRequestedEvent(
    val userId: UserId,
    val title: String,
    val body: String,
    val data: Map<String, String> = emptyMap(),
)
