package com.github.nexters.ppotto.auth.support

import com.github.nexters.ppotto.auth.application.port.AuthActiveUserPort
import com.github.nexters.ppotto.auth.application.port.AuthTermsPort
import com.github.nexters.ppotto.auth.application.port.AuthUserPort
import com.github.nexters.ppotto.auth.domain.AuthUser
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.util.UUID

@TestConfiguration(proxyBeanMethods = false)
class AuthTestConfig {
    @Bean
    @Primary
    fun authActiveUserPort(): AuthActiveUserPort = AuthActiveUserPort { true }

    @Bean
    @Primary
    fun authUserPort(): AuthUserPort = AuthUserPort { AuthUser(UUID.randomUUID(), true) }

    @Bean
    @Primary
    fun authTermsPort(): AuthTermsPort = AuthTermsPort { emptyList() }
}
