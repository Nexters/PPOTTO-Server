package com.github.nexters.ppotto.notification.application

import com.github.nexters.ppotto.global.config.AsyncConfig
import com.github.nexters.ppotto.notification.domain.PushNotificationRequestedEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class PushNotificationEventListener(
    private val pushNotificationService: PushNotificationService,
) {
    @Async(AsyncConfig.PUSH_NOTIFICATION_TASK_EXECUTOR)
    @EventListener
    fun handle(event: PushNotificationRequestedEvent) {
        pushNotificationService.send(event)
    }
}
