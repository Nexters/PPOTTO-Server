package com.github.nexters.ppotto.user.infrastructure

import com.github.nexters.ppotto.global.error.UnauthorizedException
import com.github.nexters.ppotto.user.application.port.CurrentUserProvider
import com.github.nexters.ppotto.user.application.port.SocialAccountRevoker
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class UserPortFallbackConfig {
    @Bean
    @ConditionalOnMissingBean(CurrentUserProvider::class)
    fun unavailableCurrentUserProvider(): CurrentUserProvider = CurrentUserProvider { throw UnauthorizedException() }

    @Bean
    @ConditionalOnMissingBean(SocialAccountRevoker::class)
    fun unavailableSocialAccountRevoker(): SocialAccountRevoker =
        SocialAccountRevoker { _, _ ->
            error("소셜 계정 해지 어댑터가 연결되지 않았습니다.")
        }
}
