package com.github.nexters.ppotto.user.infrastructure

import com.github.nexters.ppotto.user.application.port.SocialAccountRevoker
import com.github.nexters.ppotto.user.application.port.WithdrawnUserDataDeletionPort
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class UserPortFallbackConfig {
    @Bean
    @ConditionalOnMissingBean(SocialAccountRevoker::class)
    fun unavailableSocialAccountRevoker(): SocialAccountRevoker =
        SocialAccountRevoker { _, _ ->
            error("소셜 계정 해지 어댑터가 연결되지 않았습니다.")
        }

    @Bean
    @ConditionalOnMissingBean(WithdrawnUserDataDeletionPort::class)
    fun unavailableWithdrawnUserDataDeletionPort(): WithdrawnUserDataDeletionPort =
        WithdrawnUserDataDeletionPort {
            error("탈퇴 사용자 연관 데이터 삭제 어댑터가 연결되지 않았습니다.")
        }
}
