package com.github.nexters.ppotto.auth.infrastructure.integration

import com.github.nexters.ppotto.auth.application.AuthService
import com.github.nexters.ppotto.auth.application.AuthService.Companion.SIGNUP_TRANSACTION
import com.github.nexters.ppotto.auth.application.port.AuthActiveUserPort
import com.github.nexters.ppotto.auth.application.port.AuthTermsPort
import com.github.nexters.ppotto.auth.application.port.AuthUserPort
import com.github.nexters.ppotto.auth.application.port.RefreshTokenStore
import com.github.nexters.ppotto.auth.application.port.TokenProvider
import com.github.nexters.ppotto.auth.domain.AuthErrorCode
import com.github.nexters.ppotto.auth.domain.AuthUser
import com.github.nexters.ppotto.auth.domain.LoginCommand
import com.github.nexters.ppotto.auth.domain.SocialProfile
import com.github.nexters.ppotto.auth.domain.TokenPair
import com.github.nexters.ppotto.global.error.UnauthorizedException
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.oauth.OAuthProvider
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.ResettableFake
import com.github.nexters.ppotto.user.application.UserService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Qualifier
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
    userSessionRevoker: RefreshTokenUserSessionRevoker,
) : IntegrationTest({
        fun newUser(): AuthUser =
            authUserPort.findOrCreate(
                SocialProfile(
                    provider = OAuthProvider.KAKAO,
                    providerUserId = "session-${UUID.randomUUID()}",
                    email = "session@example.com",
                    name = "세션사용자",
                ),
            )!!

        Given("refresh token을 가진 활성 사용자가 있을 때") {
            val user = newUser()
            val refreshToken = "refresh-${UUID.randomUUID()}"
            refreshTokenStore.save(user.userId, refreshToken)

            When("탈퇴가 세션 폐기 port를 호출하면") {
                userSessionRevoker.revoke(user.userId)

                Then("저장된 refresh token 세션을 지운다") {
                    refreshTokenStore.findUserId(refreshToken).shouldBeNull()
                }
            }
        }

        Given("탈퇴한 사용자의 refresh token이 저장소에 남아 있을 때") {
            val user = newUser()
            userService.withdraw(user.userId)
            val staleRefreshToken = "stale-${UUID.randomUUID()}"
            refreshTokenStore.save(user.userId, staleRefreshToken)

            When("남아 있는 token으로 재발급을 요청하면") {
                val exception = shouldThrow<UnauthorizedException> { authService.refresh(staleRefreshToken) }

                Then("AUTH-002 예외가 발생한다") {
                    exception.errorCode shouldBe AuthErrorCode.INVALID_REFRESH_TOKEN
                }

                Then("사용자는 비활성 상태로 남는다") {
                    userService.isActive(user.userId) shouldBe false
                }

                Then("비활성 사용자에게는 token을 발급하지 않는다") {
                    tokenProvider.issueCount shouldBe 0
                }

                Then("비활성 사용자의 기존 token은 회전하지 않는다") {
                    refreshTokenStore.rotationCount shouldBe 0
                }
            }
        }

        Given("OAuth client가 하나도 등록되지 않은 AuthService에서") {
            When("카카오로 로그인하면") {
                val exception =
                    shouldThrow<IllegalStateException> { authService.login(LoginCommand.Kakao("kakao-token")) }

                Then("연결되지 않은 provider를 버그로 드러낸다") {
                    exception.message shouldBe "OAuth provider client가 연결되지 않았습니다."
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
        @Qualifier(SIGNUP_TRANSACTION) signupTransaction: TransactionOperations,
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

class SessionRefreshTokenStore :
    RefreshTokenStore,
    ResettableFake {
    private val tokens = mutableMapOf<String, UserId>()
    var rotationCount: Int = 0

    override fun save(
        userId: UserId,
        refreshToken: String,
    ) {
        tokens[refreshToken] = userId
    }

    override fun findUserId(refreshToken: String): UserId? = tokens[refreshToken]

    override fun rotate(
        userId: UserId,
        currentRefreshToken: String,
        newRefreshToken: String,
    ): Boolean {
        rotationCount += 1
        if (tokens.remove(currentRefreshToken) != userId) return false
        tokens[newRefreshToken] = userId
        return true
    }

    override fun delete(userId: UserId) {
        tokens.entries.removeIf { it.value == userId }
    }

    override fun reset() {
        tokens.clear()
        rotationCount = 0
    }
}

class SessionTokenProvider :
    TokenProvider,
    ResettableFake {
    var issueCount: Int = 0

    override fun issue(userId: UserId): TokenPair {
        issueCount += 1
        return TokenPair("access-$userId", "refresh-$userId", 3_600)
    }

    override fun verifyAccessToken(accessToken: String): UserId = UserId(UUID.fromString(accessToken))

    override fun reset() {
        issueCount = 0
    }
}
