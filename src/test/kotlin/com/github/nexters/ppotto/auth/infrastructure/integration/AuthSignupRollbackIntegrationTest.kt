package com.github.nexters.ppotto.auth.infrastructure.integration

import com.github.nexters.ppotto.auth.application.AuthService
import com.github.nexters.ppotto.auth.application.AuthService.Companion.SIGNUP_TRANSACTION
import com.github.nexters.ppotto.auth.application.port.AuthActiveUserPort
import com.github.nexters.ppotto.auth.application.port.AuthTermsPort
import com.github.nexters.ppotto.auth.application.port.AuthUserPort
import com.github.nexters.ppotto.auth.application.port.OAuthClient
import com.github.nexters.ppotto.auth.application.port.RefreshTokenStore
import com.github.nexters.ppotto.auth.application.port.TokenProvider
import com.github.nexters.ppotto.auth.domain.AuthErrorCode
import com.github.nexters.ppotto.auth.domain.LoginCommand
import com.github.nexters.ppotto.auth.domain.PendingTerm
import com.github.nexters.ppotto.auth.domain.SocialProfile
import com.github.nexters.ppotto.auth.domain.TokenPair
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.error.UnauthorizedException
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.oauth.OAuthProvider
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.ResettableFake
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.transaction.support.TransactionOperations
import org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive
import java.util.UUID

const val FAILING_TERMS_AUTH_SERVICE = "failingTermsAuthService"

@Import(SignupRollbackAuthTestConfig::class)
class AuthSignupRollbackIntegrationTest(
    authService: AuthService,
    applicationContext: ApplicationContext,
    loginEffects: LoginEffects,
    userRepository: UserRepository,
    boardRepository: BoardRepository,
) : IntegrationTest({
        Given("신규 사용자와 기본 보드를 만든 뒤 약관 조회가 실패할 때") {
            val failingTermsAuthService = applicationContext.getBean(FAILING_TERMS_AUTH_SERVICE, AuthService::class.java)
            val providerUserId = "rollback-${UUID.randomUUID()}"

            When("로그인하면") {
                val exception =
                    shouldThrow<IllegalStateException> {
                        failingTermsAuthService.login(LoginCommand.Kakao(providerUserId))
                    }

                Then("사용자와 기본 보드를 롤백하고 token을 발급하거나 저장하지 않는다") {
                    exception.message shouldBe "약관 조회 실패"
                    userRepository
                        .findBySocialAccount(OAuthProvider.KAKAO, providerUserId)
                        .shouldBeNull()
                    boardRepository.findByUserId(loginEffects.userId!!) shouldHaveSize 0
                    loginEffects.tokenIssueCount shouldBe 0
                    loginEffects.tokenSaveCount shouldBe 0
                }
            }
        }

        Given("애플 최초 가입에서 authorization code 교환이 실패할 때") {
            val providerUserId = "apple-rollback-${UUID.randomUUID()}"

            When("로그인하면") {
                val exception =
                    shouldThrow<UnauthorizedException> {
                        authService.login(
                            LoginCommand.Apple(
                                identityToken = providerUserId,
                                authorizationCode = "authorization-code",
                                rawNonce = "raw-nonce",
                                name = "애플롤백사용자",
                            ),
                        )
                    }

                Then("트랜잭션 안에서 만든 사용자와 기본 보드를 함께 롤백해 유령 애플 계정을 남기지 않는다") {
                    exception.errorCode shouldBe AuthErrorCode.APPLE_CODE_EXCHANGE_FAILED
                    loginEffects.boardCountInTransaction shouldBe 1
                    userRepository
                        .findBySocialAccount(OAuthProvider.APPLE, providerUserId)
                        .shouldBeNull()
                    boardRepository.findByUserId(loginEffects.userId!!) shouldHaveSize 0
                    loginEffects.tokenIssueCount shouldBe 0
                    loginEffects.tokenSaveCount shouldBe 0
                }
            }
        }

        Given("약관 조회까지 성공하는 신규 로그인에서") {
            val providerUserId = "boundary-${UUID.randomUUID()}"

            When("로그인하면") {
                val result = authService.login(LoginCommand.Kakao(providerUserId))

                Then("가입과 약관 조회만 트랜잭션 안에서 실행하고 provider 호출과 token 발급은 밖에서 실행한다") {
                    result.isNewUser shouldBe true
                    loginEffects.providerCallInTransaction shouldBe false
                    loginEffects.termsLookupInTransaction shouldBe true
                    loginEffects.tokenIssueInTransaction shouldBe false
                    loginEffects.tokenSaveInTransaction shouldBe false
                    boardRepository.findByUserId(loginEffects.userId!!) shouldHaveSize 1
                }
            }
        }
    })

@TestConfiguration(proxyBeanMethods = false)
class SignupRollbackAuthTestConfig {
    @Bean
    fun loginEffects(): LoginEffects = LoginEffects()

    @Bean
    @Primary
    fun signupRollbackAuthService(
        authActiveUserPort: AuthActiveUserPort,
        authUserPort: AuthUserPort,
        authTermsPort: AuthTermsPort,
        boardRepository: BoardRepository,
        loginEffects: LoginEffects,
        @Qualifier(SIGNUP_TRANSACTION) signupTransaction: TransactionOperations,
    ): AuthService =
        rollbackAuthService(
            authActiveUserPort,
            recordingUserPort(authUserPort, boardRepository, loginEffects),
            TrackingTermsPort(loginEffects, authTermsPort, failTerms = false),
            loginEffects,
            signupTransaction,
        )

    @Bean(FAILING_TERMS_AUTH_SERVICE)
    fun failingTermsAuthService(
        authActiveUserPort: AuthActiveUserPort,
        authUserPort: AuthUserPort,
        authTermsPort: AuthTermsPort,
        boardRepository: BoardRepository,
        loginEffects: LoginEffects,
        @Qualifier(SIGNUP_TRANSACTION) signupTransaction: TransactionOperations,
    ): AuthService =
        rollbackAuthService(
            authActiveUserPort,
            recordingUserPort(authUserPort, boardRepository, loginEffects),
            TrackingTermsPort(loginEffects, authTermsPort, failTerms = true),
            loginEffects,
            signupTransaction,
        )

    private companion object {
        fun recordingUserPort(
            delegate: AuthUserPort,
            boardRepository: BoardRepository,
            loginEffects: LoginEffects,
        ) = AuthUserPort { profile ->
            delegate.findOrCreate(profile)?.also {
                loginEffects.userId = it.userId
                loginEffects.boardCountInTransaction = boardRepository.findByUserId(it.userId).size
            }
        }

        fun rollbackAuthService(
            authActiveUserPort: AuthActiveUserPort,
            authUserPort: AuthUserPort,
            authTermsPort: AuthTermsPort,
            loginEffects: LoginEffects,
            signupTransaction: TransactionOperations,
        ) = AuthService(
            oauthClients = listOf(TrackingOAuthClient(loginEffects), ExchangeFailureAppleOAuthClient()),
            signupTransaction = signupTransaction,
            tokenProvider = TrackingTokenProvider(loginEffects),
            refreshTokenStore = TrackingRefreshTokenStore(loginEffects),
            authUserPort = authUserPort,
            authTermsPort = authTermsPort,
            authActiveUserPort = authActiveUserPort,
        )
    }
}

class LoginEffects : ResettableFake {
    var userId: UserId? = null
    var providerCallInTransaction: Boolean = false
    var termsLookupInTransaction: Boolean = false
    var tokenIssueInTransaction: Boolean = false
    var tokenSaveInTransaction: Boolean = false
    var tokenIssueCount: Int = 0
    var tokenSaveCount: Int = 0
    var boardCountInTransaction: Int = 0

    override fun reset() {
        userId = null
        providerCallInTransaction = false
        termsLookupInTransaction = false
        tokenIssueInTransaction = false
        tokenSaveInTransaction = false
        tokenIssueCount = 0
        tokenSaveCount = 0
        boardCountInTransaction = 0
    }
}

private class TrackingOAuthClient(
    private val loginEffects: LoginEffects,
) : OAuthClient {
    override val provider = OAuthProvider.KAKAO

    override fun authenticate(command: LoginCommand): SocialProfile {
        loginEffects.providerCallInTransaction = isActualTransactionActive()
        return SocialProfile(
            provider = provider,
            providerUserId = (command as LoginCommand.Kakao).accessToken,
            email = "rollback@example.com",
            name = "롤백사용자",
        )
    }

    override fun revoke(providerRefreshToken: String) = Unit
}

private class ExchangeFailureAppleOAuthClient : OAuthClient {
    override val provider = OAuthProvider.APPLE

    override fun authenticate(command: LoginCommand): SocialProfile =
        SocialProfile(
            provider = provider,
            providerUserId = (command as LoginCommand.Apple).identityToken,
            email = "apple-rollback@example.com",
            name = command.name,
            authorizationCodeExchangeFailed = true,
        )

    override fun revoke(providerRefreshToken: String) = Unit
}

private class TrackingTermsPort(
    private val loginEffects: LoginEffects,
    private val delegate: AuthTermsPort,
    private val failTerms: Boolean,
) : AuthTermsPort {
    override fun findPendingTerms(userId: UserId): List<PendingTerm> {
        loginEffects.termsLookupInTransaction = isActualTransactionActive()
        check(!failTerms) { "약관 조회 실패" }
        return delegate.findPendingTerms(userId)
    }
}

private class TrackingTokenProvider(
    private val loginEffects: LoginEffects,
) : TokenProvider {
    override fun issue(userId: UserId): TokenPair {
        loginEffects.tokenIssueInTransaction = isActualTransactionActive()
        loginEffects.tokenIssueCount += 1
        return TokenPair("access-$userId", "refresh-$userId", 3_600)
    }

    override fun verifyAccessToken(accessToken: String): UserId = UserId(UUID.fromString(accessToken))
}

private class TrackingRefreshTokenStore(
    private val loginEffects: LoginEffects,
) : RefreshTokenStore {
    override fun save(
        userId: UserId,
        refreshToken: String,
    ) {
        loginEffects.tokenSaveInTransaction = isActualTransactionActive()
        loginEffects.tokenSaveCount += 1
    }

    override fun findUserId(refreshToken: String): UserId? = null

    override fun rotate(
        userId: UserId,
        currentRefreshToken: String,
        newRefreshToken: String,
    ): Boolean = false

    override fun delete(userId: UserId) = Unit
}
