package com.github.nexters.ppotto.auth.application

import com.github.nexters.ppotto.auth.application.port.AuthActiveUserPort
import com.github.nexters.ppotto.auth.application.port.AuthTermsPort
import com.github.nexters.ppotto.auth.application.port.AuthUserPort
import com.github.nexters.ppotto.auth.application.port.OAuthClient
import com.github.nexters.ppotto.auth.application.port.RefreshTokenStore
import com.github.nexters.ppotto.auth.application.port.TokenProvider
import com.github.nexters.ppotto.auth.domain.AuthErrorCode
import com.github.nexters.ppotto.auth.domain.AuthUser
import com.github.nexters.ppotto.auth.domain.LoginCommand
import com.github.nexters.ppotto.auth.domain.OAuthProvider
import com.github.nexters.ppotto.auth.domain.SocialProfile
import com.github.nexters.ppotto.auth.domain.TokenPair
import com.github.nexters.ppotto.global.error.UnauthorizedException
import com.github.nexters.ppotto.global.identifier.UserId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.transaction.support.TransactionOperations
import java.util.UUID

class AuthServiceTest :
    BehaviorSpec({
        val userId = UserId(UUID.randomUUID())
        val noTransaction = TransactionOperations.withoutTransaction()
        val refreshStore = FakeRefreshTokenStore()
        val tokenProvider =
            object : TokenProvider {
                override fun issue(userId: UserId) = TokenPair("access-$userId", "refresh-$userId-${UUID.randomUUID()}", 3600)

                override fun verifyAccessToken(accessToken: String) = userId
            }
        val termsPort = AuthTermsPort { emptyList() }
        val activeUserPort = AuthActiveUserPort { true }

        Given("소셜 계정이 처음 가입하고 provider 검증이 성공했을 때") {
            val oauthClient = FakeOAuthClient()
            val userPort = AuthUserPort { AuthUser(userId, true) }
            val service =
                AuthService(
                    oauthClients = listOf(oauthClient),
                    signupTransaction = noTransaction,
                    tokenProvider = tokenProvider,
                    refreshTokenStore = refreshStore,
                    authUserPort = userPort,
                    authTermsPort = termsPort,
                    authActiveUserPort = activeUserPort,
                )

            When("로그인하면") {
                Then("신규 사용자 여부와 저장된 서비스 토큰을 반환한다") {
                    val result = service.login(LoginCommand.Kakao("kakao-token"))

                    result.isNewUser shouldBe true
                    refreshStore.findUserId(result.tokenPair.refreshToken) shouldBe userId
                }
            }
        }

        Given("애플 최초 가입에서 authorization code 교환이 실패했을 때") {
            val oauthClient = FakeOAuthClient(exchangeFailed = true)
            val userPort = AuthUserPort { AuthUser(userId, true) }
            val service =
                AuthService(
                    oauthClients = listOf(oauthClient),
                    signupTransaction = noTransaction,
                    tokenProvider = tokenProvider,
                    refreshTokenStore = refreshStore,
                    authUserPort = userPort,
                    authTermsPort = termsPort,
                    authActiveUserPort = activeUserPort,
                )

            When("로그인하면") {
                Then("AUTH-003 예외가 발생한다") {
                    val exception =
                        shouldThrow<UnauthorizedException> {
                            service.login(LoginCommand.Kakao("apple-command-placeholder"))
                        }
                    exception.errorCode shouldBe AuthErrorCode.APPLE_CODE_EXCHANGE_FAILED
                }
            }
        }

        Given("애플 기존 사용자의 authorization code 교환이 실패했을 때") {
            val oauthClient = FakeOAuthClient(exchangeFailed = true)
            val userPort = AuthUserPort { AuthUser(userId, false) }
            val service =
                AuthService(
                    oauthClients = listOf(oauthClient),
                    signupTransaction = noTransaction,
                    tokenProvider = tokenProvider,
                    refreshTokenStore = refreshStore,
                    authUserPort = userPort,
                    authTermsPort = termsPort,
                    authActiveUserPort = activeUserPort,
                )

            When("로그인하면") {
                Then("기존 사용자는 로그인을 계속한다") {
                    service.login(LoginCommand.Kakao("apple-command-placeholder")).isNewUser shouldBe false
                }
            }
        }

        Given("한 번 사용한 refresh token이 주어졌을 때") {
            val oauthClient = FakeOAuthClient()
            val userPort = AuthUserPort { AuthUser(userId, false) }
            val service =
                AuthService(
                    oauthClients = listOf(oauthClient),
                    signupTransaction = noTransaction,
                    tokenProvider = tokenProvider,
                    refreshTokenStore = refreshStore,
                    authUserPort = userPort,
                    authTermsPort = termsPort,
                    authActiveUserPort = activeUserPort,
                )
            val issued = service.login(LoginCommand.Kakao("kakao-token")).tokenPair
            service.refresh(issued.refreshToken)

            When("같은 refresh token으로 다시 재발급하면") {
                Then("AUTH-002 예외가 발생한다") {
                    val exception =
                        shouldThrow<UnauthorizedException> {
                            service.refresh(issued.refreshToken)
                        }
                    exception.errorCode shouldBe AuthErrorCode.INVALID_REFRESH_TOKEN
                }
            }
        }

        Given("탈퇴한 사용자의 refresh token이 저장소에 남아 있을 때") {
            val oauthClient = FakeOAuthClient()
            val userPort = AuthUserPort { AuthUser(userId, false) }
            val inactiveUserPort = AuthActiveUserPort { false }
            val service =
                AuthService(
                    oauthClients = listOf(oauthClient),
                    signupTransaction = noTransaction,
                    tokenProvider = tokenProvider,
                    refreshTokenStore = refreshStore,
                    authUserPort = userPort,
                    authTermsPort = termsPort,
                    authActiveUserPort = inactiveUserPort,
                )
            val refreshToken = "withdrawn-${UUID.randomUUID()}"
            refreshStore.save(userId, refreshToken)

            When("토큰 재발급을 요청하면") {
                Then("AUTH-002 예외가 발생한다") {
                    val exception =
                        shouldThrow<UnauthorizedException> {
                            service.refresh(refreshToken)
                        }
                    exception.errorCode shouldBe AuthErrorCode.INVALID_REFRESH_TOKEN
                }
            }
        }
    }) {
    private class FakeOAuthClient(
        private val exchangeFailed: Boolean = false,
    ) : OAuthClient {
        override val provider = OAuthProvider.KAKAO

        override fun authenticate(command: LoginCommand) =
            SocialProfile(
                provider,
                "provider-user-id",
                "user@example.com",
                authorizationCodeExchangeFailed = exchangeFailed,
            )

        override fun revoke(providerRefreshToken: String) = Unit
    }

    private class FakeRefreshTokenStore : RefreshTokenStore {
        private val tokens = mutableMapOf<String, UserId>()

        override fun save(
            userId: UserId,
            refreshToken: String,
        ) {
            tokens[refreshToken] = userId
        }

        override fun findUserId(refreshToken: String) = tokens[refreshToken]

        override fun rotate(
            userId: UserId,
            currentRefreshToken: String,
            newRefreshToken: String,
        ): Boolean {
            if (tokens.remove(currentRefreshToken) != userId) return false
            tokens[newRefreshToken] = userId
            return true
        }

        override fun delete(userId: UserId) {
            tokens.entries.removeIf { it.value == userId }
        }
    }
}
