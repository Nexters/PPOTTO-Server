package com.github.nexters.ppotto.user.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration(proxyBeanMethods = false)
class UserTestConfig {
    @Bean
    @Primary
    fun fakeSocialAccountRevoker(): FakeSocialAccountRevoker = FakeSocialAccountRevoker()

    @Bean
    @Primary
    fun fakeWithdrawnUserDataDeletionPort(): FakeWithdrawnUserDataDeletionPort = FakeWithdrawnUserDataDeletionPort()
}
