package com.github.nexters.ppotto.notification.support

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile

@Configuration(proxyBeanMethods = false)
@Profile("test")
class NotificationTestConfig {
    @Bean
    @Primary
    fun pushNotifier(): FakePushNotifier = FakePushNotifier()
}
