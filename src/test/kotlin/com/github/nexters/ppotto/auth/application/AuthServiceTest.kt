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
import com.github.nexters.ppotto.auth.domain.SocialProfile
import com.github.nexters.ppotto.auth.domain.TokenPair
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.error.UnauthorizedException
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.oauth.OAuthProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import org.springframework.transaction.support.TransactionOperations
import java.util.UUID

private val KAKAO_LOGIN = LoginCommand.Kakao("kakao-token")

private val APPLE_LOGIN =
    LoginCommand.Apple(
        identityToken = "apple-identity-token",
        authorizationCode = "apple-authorization-code",
        rawNonce = "apple-raw-nonce",
        name = "애플사용자",
    )

class AuthServiceTest :
    BehaviorSpec({
        val userId = UserId(UUID.randomUUID())
        val noTransaction = TransactionOperations.withoutTransaction()
        val tokenProvider =
            object : TokenProvider {
                override fun issue(userId: UserId) = TokenPair("access-$userId", "refresh-$userId-${UUID.randomUUID()}", 3600)

                override fun verifyAccessToken(accessToken: String) = userId
            }
        val termsPort = AuthTermsPort { emptyList() }
        val activeUserPort = AuthActiveUserPort { true }

        fun authService(
            oauthClient: OAuthClient,
            userPort: AuthUserPort,
            refreshStore: RefreshTokenStore,
            activePort: AuthActiveUserPort = activeUserPort,
        ) = AuthService(
            oauthClients = listOf(oauthClient),
            signupTransaction = noTransaction,
            tokenProvider = tokenProvider,
            refreshTokenStore = refreshStore,
            authUserPort = userPort,
            authTermsPort = termsPort,
            authActiveUserPort = activePort,
        )

        Given("소셜 계정이 처음 가입하고 provider 검증이 성공했을 때") {
            val refreshStore = FakeRefreshTokenStore()
            val service = authService(FakeOAuthClient(), AuthUserPort { AuthUser(userId, true) }, refreshStore)

            When("로그인하면") {
                val result = service.login(KAKAO_LOGIN)

                Then("신규 사용자로 응답한다") {
                    result.isNewUser.shouldBeTrue()
                }

                Then("발급한 refresh token을 저장소에 남긴다") {
                    refreshStore.findUserId(result.tokenPair.refreshToken) shouldBe userId
                }
            }
        }

        Given("애플 최초 가입에서 authorization code 교환이 실패했을 때") {
            val client = FakeOAuthClient(provider = OAuthProvider.APPLE, exchangeFailed = true)
            val service = authService(client, AuthUserPort { AuthUser(userId, true) }, FakeRefreshTokenStore())

            When("로그인하면") {
                val exception = shouldThrow<UnauthorizedException> { service.login(APPLE_LOGIN) }

                Then("AUTH-003 예외가 발생한다") {
                    exception.errorCode shouldBe AuthErrorCode.APPLE_CODE_EXCHANGE_FAILED
                }
            }
        }

        Given("가입에 필요한 이름 없이 신규 가입을 시도했을 때") {
            val service = authService(FakeOAuthClient(), AuthUserPort { null }, FakeRefreshTokenStore())

            When("로그인하면") {
                val exception = shouldThrow<InvalidInputException> { service.login(KAKAO_LOGIN) }

                Then("AUTH-006 예외가 발생한다") {
                    exception.errorCode shouldBe AuthErrorCode.SIGNUP_NAME_REQUIRED
                }
            }
        }

        Given("가입에 필요한 이메일 없이 신규 가입을 시도했을 때") {
            val client = FakeOAuthClient(provider = OAuthProvider.APPLE, email = null)
            val service = authService(client, AuthUserPort { null }, FakeRefreshTokenStore())

            When("로그인하면") {
                val exception = shouldThrow<InvalidInputException> { service.login(APPLE_LOGIN) }

                Then("AUTH-007 예외가 발생한다") {
                    exception.errorCode shouldBe AuthErrorCode.SIGNUP_EMAIL_REQUIRED
                }
            }
        }

        Given("이메일 없이 기존 사용자가 다시 로그인했을 때") {
            val client = FakeOAuthClient(provider = OAuthProvider.APPLE, email = null)
            val service = authService(client, AuthUserPort { AuthUser(userId, false) }, FakeRefreshTokenStore())

            When("로그인하면") {
                val result = service.login(APPLE_LOGIN)

                Then("기존 사용자로 로그인에 성공한다") {
                    result.isNewUser.shouldBeFalse()
                }
            }
        }

        Given("애플 기존 사용자의 authorization code 교환이 실패했을 때") {
            val client = FakeOAuthClient(provider = OAuthProvider.APPLE, exchangeFailed = true)
            val service = authService(client, AuthUserPort { AuthUser(userId, false) }, FakeRefreshTokenStore())

            When("로그인하면") {
                val result = service.login(APPLE_LOGIN)

                Then("기존 사용자는 로그인을 계속한다") {
                    result.isNewUser.shouldBeFalse()
                }
            }
        }

        Given("한 번 사용한 refresh token이 주어졌을 때") {
            val refreshStore = FakeRefreshTokenStore()
            val service = authService(FakeOAuthClient(), AuthUserPort { AuthUser(userId, false) }, refreshStore)
            val issued = service.login(KAKAO_LOGIN).tokenPair
            service.refresh(issued.refreshToken)

            When("같은 refresh token으로 다시 재발급하면") {
                val exception = shouldThrow<UnauthorizedException> { service.refresh(issued.refreshToken) }

                Then("AUTH-002 예외가 발생한다") {
                    exception.errorCode shouldBe AuthErrorCode.INVALID_REFRESH_TOKEN
                }
            }
        }

        Given("탈퇴한 사용자의 refresh token이 저장소에 남아 있을 때") {
            val refreshStore = FakeRefreshTokenStore()
            val service =
                authService(
                    FakeOAuthClient(),
                    AuthUserPort { AuthUser(userId, false) },
                    refreshStore,
                    activePort = AuthActiveUserPort { false },
                )
            val refreshToken = "withdrawn-${UUID.randomUUID()}"
            refreshStore.save(userId, refreshToken)

            When("토큰 재발급을 요청하면") {
                val exception = shouldThrow<UnauthorizedException> { service.refresh(refreshToken) }

                Then("AUTH-002 예외가 발생한다") {
                    exception.errorCode shouldBe AuthErrorCode.INVALID_REFRESH_TOKEN
                }

                Then("비활성 사용자의 기존 token은 회전하지 않는다") {
                    refreshStore.findUserId(refreshToken) shouldBe userId
                }
            }
        }
    }) {
    private class FakeOAuthClient(
        override val provider: OAuthProvider = OAuthProvider.KAKAO,
        private val exchangeFailed: Boolean = false,
        private val email: String? = "user@example.com",
    ) : OAuthClient {
        override fun authenticate(command: LoginCommand) =
            SocialProfile(
                provider,
                "provider-user-id",
                email,
                "테스트사용자",
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
