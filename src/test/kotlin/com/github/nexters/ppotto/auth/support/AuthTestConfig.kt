package com.github.nexters.ppotto.auth.support

import com.github.nexters.ppotto.auth.application.port.AuthTermsPort
import com.github.nexters.ppotto.auth.application.port.AuthUserPort
import com.github.nexters.ppotto.auth.domain.AuthUser
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import java.util.UUID

@TestConfiguration(proxyBeanMethods = false)
class AuthTestConfig {
    @Bean
    fun authUserPort(): AuthUserPort = AuthUserPort { AuthUser(UUID.randomUUID(), true) }

    @Bean
    fun authTermsPort(): AuthTermsPort = AuthTermsPort { emptyList() }
}
