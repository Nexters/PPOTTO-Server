package com.github.nexters.ppotto.auth.infrastructure.integration

import com.github.nexters.ppotto.auth.application.AuthService
import com.github.nexters.ppotto.auth.application.port.AuthActiveUserPort
import com.github.nexters.ppotto.auth.application.port.AuthTermsPort
import com.github.nexters.ppotto.auth.application.port.AuthUserPort
import com.github.nexters.ppotto.auth.application.port.RefreshTokenStore
import com.github.nexters.ppotto.auth.application.port.TokenProvider
import com.github.nexters.ppotto.auth.domain.AuthErrorCode
import com.github.nexters.ppotto.auth.domain.OAuthProvider
import com.github.nexters.ppotto.auth.domain.SocialProfile
import com.github.nexters.ppotto.auth.domain.TokenPair
import com.github.nexters.ppotto.global.error.UnauthorizedException
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.user.application.UserService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.transaction.support.TransactionOperations
import java.util.UUID

@Import(AuthSessionTestConfig::class)
class AuthSessionIntegrationTest(
    authService: AuthService,
    authUserPort: AuthUserPort,
    userService: UserService,
    refreshTokenStore: SessionRefreshTokenStore,
    tokenProvider: SessionTokenProvider,
) : IntegrationTest({
        Given("refresh token을 가진 활성 사용자가 있을 때") {
            refreshTokenStore.clear()
            tokenProvider.reset()
            val user =
                authUserPort.findOrCreate(
                    SocialProfile(
                        provider = OAuthProvider.KAKAO,
                        providerUserId = "session-${UUID.randomUUID()}",
                        email = "session@example.com",
                    ),
                )
            val refreshToken = "refresh-${UUID.randomUUID()}"
            refreshTokenStore.save(user.userId, refreshToken)

            When("탈퇴한 뒤 남아 있는 token으로 재발급을 시도하면") {
                userService.withdraw(user.userId)
                refreshTokenStore.findUserId(refreshToken).shouldBeNull()

                val staleRefreshToken = "stale-${UUID.randomUUID()}"
                refreshTokenStore.save(user.userId, staleRefreshToken)
                val exception =
                    shouldThrow<UnauthorizedException> {
                        authService.refresh(staleRefreshToken)
                    }

                Then("세션을 폐기하고 비활성 사용자의 token 발급과 rotation을 거부한다") {
                    userService.isActive(user.userId) shouldBe false
                    exception.errorCode shouldBe AuthErrorCode.INVALID_REFRESH_TOKEN
                    tokenProvider.issueCount shouldBe 0
                    refreshTokenStore.rotationCount shouldBe 0
                }
            }
        }
    })

@TestConfiguration(proxyBeanMethods = false)
class AuthSessionTestConfig {
    @Bean
    @Primary
    fun sessionRefreshTokenStore(): SessionRefreshTokenStore = SessionRefreshTokenStore()

    @Bean
    @Primary
    fun sessionTokenProvider(): SessionTokenProvider = SessionTokenProvider()

    @Bean
    @Primary
    fun sessionAuthService(
        authActiveUserPort: AuthActiveUserPort,
        authTermsPort: AuthTermsPort,
        authUserPort: AuthUserPort,
        refreshTokenStore: SessionRefreshTokenStore,
        tokenProvider: SessionTokenProvider,
        signupTransaction: TransactionOperations,
    ): AuthService =
        AuthService(
            oauthClients = emptyList(),
            signupTransaction = signupTransaction,
            tokenProvider = tokenProvider,
            refreshTokenStore = refreshTokenStore,
            authUserPort = authUserPort,
            authTermsPort = authTermsPort,
            authActiveUserPort = authActiveUserPort,
        )
}

class SessionRefreshTokenStore : RefreshTokenStore {
    private val tokens = mutableMapOf<String, UUID>()
    var rotationCount: Int = 0

    override fun save(
        userId: UUID,
        refreshToken: String,
    ) {
        tokens[refreshToken] = userId
    }

    override fun findUserId(refreshToken: String): UUID? = tokens[refreshToken]

    override fun rotate(
        userId: UUID,
        currentRefreshToken: String,
        newRefreshToken: String,
    ): Boolean {
        rotationCount += 1
        if (tokens.remove(currentRefreshToken) != userId) return false
        tokens[newRefreshToken] = userId
        return true
    }

    override fun delete(userId: UUID) {
        tokens.entries.removeIf { it.value == userId }
    }

    fun clear() {
        tokens.clear()
        rotationCount = 0
    }
}

class SessionTokenProvider : TokenProvider {
    var issueCount: Int = 0

    override fun issue(userId: UUID): TokenPair {
        issueCount += 1
        return TokenPair("access-$userId", "refresh-$userId", 3_600)
    }

    override fun verifyAccessToken(accessToken: String): UUID = UUID.fromString(accessToken)

    fun reset() {
        issueCount = 0
    }
}
