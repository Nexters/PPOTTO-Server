package com.github.nexters.ppotto.support

import com.github.nexters.ppotto.auth.application.AuthService
import com.github.nexters.ppotto.auth.application.port.AuthActiveUserPort
import com.github.nexters.ppotto.auth.application.port.AuthTermsPort
import com.github.nexters.ppotto.auth.application.port.AuthUserPort
import com.github.nexters.ppotto.auth.application.port.OAuthClient
import com.github.nexters.ppotto.auth.application.port.RefreshTokenStore
import com.github.nexters.ppotto.auth.application.port.TokenProvider
import com.github.nexters.ppotto.auth.domain.LoginCommand
import com.github.nexters.ppotto.auth.domain.OAuthProvider
import com.github.nexters.ppotto.auth.domain.SocialProfile
import com.github.nexters.ppotto.global.identifier.UserId
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.transaction.support.TransactionOperations

@TestConfiguration(proxyBeanMethods = false)
class UserJourneyTestConfig {
    @Bean
    @Primary
    fun journeyRefreshTokenStore(): InMemoryRefreshTokenStore = InMemoryRefreshTokenStore()

    @Bean
    @Primary
    fun journeyAuthService(
        tokenProvider: TokenProvider,
        refreshTokenStore: InMemoryRefreshTokenStore,
        authUserPort: AuthUserPort,
        authTermsPort: AuthTermsPort,
        authActiveUserPort: AuthActiveUserPort,
        signupTransaction: TransactionOperations,
    ): AuthService =
        AuthService(
            oauthClients = listOf(StubKakaoOAuthClient()),
            signupTransaction = signupTransaction,
            tokenProvider = tokenProvider,
            refreshTokenStore = refreshTokenStore,
            authUserPort = authUserPort,
            authTermsPort = authTermsPort,
            authActiveUserPort = authActiveUserPort,
        )
}

class StubKakaoOAuthClient : OAuthClient {
    override val provider = OAuthProvider.KAKAO

    override fun authenticate(command: LoginCommand): SocialProfile =
        (command as LoginCommand.Kakao).accessToken.let {
            SocialProfile(
                provider = provider,
                providerUserId = it,
                email = "$it@example.com",
                name = "여정테스트사용자",
            )
        }

    override fun revoke(providerRefreshToken: String) = Unit
}

class InMemoryRefreshTokenStore : RefreshTokenStore {
    private val userIdsByToken = mutableMapOf<String, UserId>()

    override fun save(
        userId: UserId,
        refreshToken: String,
    ) {
        userIdsByToken[refreshToken] = userId
    }

    override fun findUserId(refreshToken: String): UserId? = userIdsByToken[refreshToken]

    override fun rotate(
        userId: UserId,
        currentRefreshToken: String,
        newRefreshToken: String,
    ): Boolean =
        userIdsByToken
            .remove(currentRefreshToken)
            ?.takeIf { it == userId }
            ?.let { userIdsByToken.put(newRefreshToken, it) == null }
            ?: false

    override fun delete(userId: UserId) {
        userIdsByToken.entries.removeIf { it.value == userId }
    }
}
