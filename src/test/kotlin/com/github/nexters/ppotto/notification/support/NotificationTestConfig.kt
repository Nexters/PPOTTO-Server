package com.github.nexters.ppotto.notification.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class NotificationTestConfig {
    @Bean
    @Primary
    fun pushNotifier(): FakePushNotifier = FakePushNotifier()
}
