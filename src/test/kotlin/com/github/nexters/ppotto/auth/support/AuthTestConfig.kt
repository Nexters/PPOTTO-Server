package com.github.nexters.ppotto.auth.support

import com.github.nexters.ppotto.auth.application.AuthService
import com.github.nexters.ppotto.auth.application.AuthService.Companion.SIGNUP_TRANSACTION
import com.github.nexters.ppotto.auth.application.port.AuthActiveUserPort
import com.github.nexters.ppotto.auth.application.port.AuthTermsPort
import com.github.nexters.ppotto.auth.application.port.AuthUserPort
import com.github.nexters.ppotto.auth.application.port.OAuthClient
import com.github.nexters.ppotto.auth.application.port.TokenProvider
import com.github.nexters.ppotto.auth.domain.AuthUser
import com.github.nexters.ppotto.auth.domain.LoginCommand
import com.github.nexters.ppotto.auth.domain.SocialProfile
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.oauth.OAuthProvider
import com.github.nexters.ppotto.support.InMemoryRefreshTokenStore
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.transaction.support.TransactionOperations
import java.util.UUID

@TestConfiguration(proxyBeanMethods = false)
class AuthTestConfig {
    @Bean
    @Primary
    fun authActiveUserPort(): AuthActiveUserPort = AuthActiveUserPort { true }

    @Bean
    @Primary
    fun authUserPort(): AuthUserPort = AuthUserPort { AuthUser(UserId(UUID.randomUUID()), true) }

    @Bean
    @Primary
    fun authTermsPort(): AuthTermsPort = AuthTermsPort { emptyList() }

    @Bean
    @Primary
    fun authRefreshTokenStore(): InMemoryRefreshTokenStore = InMemoryRefreshTokenStore()

    @Bean
    @Primary
    fun stubbedAuthService(
        authActiveUserPort: AuthActiveUserPort,
        authUserPort: AuthUserPort,
        authTermsPort: AuthTermsPort,
        tokenProvider: TokenProvider,
        refreshTokenStore: InMemoryRefreshTokenStore,
        @Qualifier(SIGNUP_TRANSACTION) signupTransaction: TransactionOperations,
    ): AuthService =
        AuthService(
            oauthClients = listOf(StubAuthOAuthClient()),
            signupTransaction = signupTransaction,
            tokenProvider = tokenProvider,
            refreshTokenStore = refreshTokenStore,
            authUserPort = authUserPort,
            authTermsPort = authTermsPort,
            authActiveUserPort = authActiveUserPort,
        )
}

class StubAuthOAuthClient : OAuthClient {
    override val provider = OAuthProvider.KAKAO

    override fun authenticate(command: LoginCommand): SocialProfile {
        val providerUserId =
            when (command) {
                is LoginCommand.Kakao -> command.accessToken
                is LoginCommand.KakaoWeb -> command.authorizationCode
                is LoginCommand.Apple -> error("카카오 stub client에 애플 명령이 전달되었습니다.")
            }
        return SocialProfile(
            provider = provider,
            providerUserId = providerUserId,
            email = "$providerUserId@example.com",
            name = "인증컨트롤러사용자",
        )
    }

    override fun revoke(providerRefreshToken: String) = Unit
}
