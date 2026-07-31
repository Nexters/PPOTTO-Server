package com.github.nexters.ppotto.auth.infrastructure.integration

import com.github.nexters.ppotto.auth.application.AuthService
import com.github.nexters.ppotto.auth.application.AuthService.Companion.SIGNUP_TRANSACTION
import com.github.nexters.ppotto.auth.application.port.AuthActiveUserPort
import com.github.nexters.ppotto.auth.application.port.AuthTermsPort
import com.github.nexters.ppotto.auth.application.port.AuthUserPort
import com.github.nexters.ppotto.auth.application.port.OAuthClient
import com.github.nexters.ppotto.auth.application.port.RefreshTokenStore
import com.github.nexters.ppotto.auth.application.port.TokenProvider
import com.github.nexters.ppotto.auth.config.AuthTransactionConfig.Companion.SIGNUP_TRANSACTION_TIMEOUT_SECONDS
import com.github.nexters.ppotto.auth.domain.AuthErrorCode
import com.github.nexters.ppotto.auth.domain.LoginCommand
import com.github.nexters.ppotto.auth.domain.OAuthProvider
import com.github.nexters.ppotto.auth.domain.PendingTerm
import com.github.nexters.ppotto.auth.domain.SocialProfile
import com.github.nexters.ppotto.auth.domain.TokenPair
import com.github.nexters.ppotto.board.application.BoardAccessService
import com.github.nexters.ppotto.board.application.BoardCommandService
import com.github.nexters.ppotto.board.application.BoardDrawingCommandService
import com.github.nexters.ppotto.board.application.port.BoardAnalysisActivityPort
import com.github.nexters.ppotto.board.application.port.BoardStickerCommandPort
import com.github.nexters.ppotto.board.domain.Board
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.error.UnauthorizedException
import com.github.nexters.ppotto.jooq.tables.references.TERMS
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.runConcurrently
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.transaction.support.TransactionOperations
import org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID
import com.github.nexters.ppotto.user.domain.OAuthProvider as UserOAuthProvider

class AuthDomainIntegrationTest(
    authService: AuthService,
    authUserPort: AuthUserPort,
    authTermsPort: AuthTermsPort,
    userRepository: UserRepository,
    boardRepository: BoardRepository,
    dslContext: DSLContext,
    applicationContext: ApplicationContext,
) : IntegrationTest({
        Given("production auth 도메인 adapter가 연결된 상태에서") {
            Then("AuthService bean을 생성한다") {
                authService.javaClass.simpleName
                    .isNotBlank() shouldBe true
            }

            Then("가입 구간은 공유 template과 분리된 전용 트랜잭션 bean으로 timeout을 제한한다") {
                applicationContext
                    .getBean(SIGNUP_TRANSACTION, TransactionTemplate::class.java)
                    .also { it.timeout shouldBe SIGNUP_TRANSACTION_TIMEOUT_SECONDS }
                    .shouldNotBeSameInstanceAs(applicationContext.getBean("transactionTemplate", TransactionTemplate::class.java))
            }
        }

        Given("같은 Apple 소셜 계정으로 두 번 로그인할 때") {
            val providerUserId = "existing-${UUID.randomUUID()}"
            val profile =
                SocialProfile(
                    provider = OAuthProvider.APPLE,
                    providerUserId = providerUserId,
                    email = "existing@example.com",
                    providerRefreshToken = "provider-refresh-token",
                )

            When("사용자를 두 번 조회하거나 생성하면") {
                val first = authUserPort.findOrCreate(profile)
                val second = authUserPort.findOrCreate(profile)

                Then("기존 사용자에게 기본 보드를 중복 생성하지 않는다") {
                    first.isNewUser shouldBe true
                    second.isNewUser shouldBe false
                    second.userId shouldBe first.userId
                    userRepository
                        .findBySocialAccount(UserOAuthProvider.APPLE, providerUserId)
                        ?.id shouldBe first.userId
                    boardRepository.findByUserId(first.userId) shouldHaveSize 1
                }
            }
        }

        Given("같은 소셜 계정으로 신규 로그인이 동시에 들어올 때") {
            val providerUserId = "concurrent-${UUID.randomUUID()}"
            val profile =
                SocialProfile(
                    provider = OAuthProvider.KAKAO,
                    providerUserId = providerUserId,
                    email = "concurrent@example.com",
                )

            When("가입 port를 동시에 호출하면") {
                val users =
                    runConcurrently(6) { authUserPort.findOrCreate(profile) }
                        .map { it.getOrThrow() }

                Then("계정과 기본 보드를 각각 한 번만 생성한다") {
                    users.map { it.userId }.distinct() shouldHaveSize 1
                    users.count { it.isNewUser } shouldBe 1
                    boardRepository.findByUserId(users.first().userId) shouldHaveSize 1
                    userRepository
                        .findBySocialAccount(UserOAuthProvider.KAKAO, providerUserId)
                        ?.id shouldBe users.first().userId
                }
            }
        }

        Given("현재 약관에 동의하지 않은 사용자가 있을 때") {
            val user =
                authUserPort.findOrCreate(
                    SocialProfile(
                        provider = OAuthProvider.KAKAO,
                        providerUserId = "terms-${UUID.randomUUID()}",
                        email = "terms@example.com",
                    ),
                )
            val code = "AUTH-${UUID.randomUUID()}"
            val term =
                dslContext
                    .insertInto(
                        TERMS,
                        TERMS.CODE,
                        TERMS.VERSION,
                        TERMS.IS_REQUIRED,
                        TERMS.CONTENT_URL,
                        TERMS.EFFECTIVE_AT,
                    ).values(
                        code,
                        "1.0",
                        true,
                        "https://example.com/$code",
                        Instant.now().minusSeconds(60),
                    ).returning()
                    .fetchOne()!!

            When("로그인용 미동의 약관을 조회하면") {
                val pending = authTermsPort.findPendingTerms(user.userId).single { it.id == term.id }

                Then("terms 결과를 auth 응답 dto로 명시적으로 변환한다") {
                    pending.code shouldBe code
                    pending.version shouldBe "1.0"
                    pending.isRequired shouldBe true
                    pending.contentUrl shouldBe "https://example.com/$code"
                    pending.agreed shouldBe false
                }
            }
        }
    })

@Import(SignupRollbackAuthTestConfig::class)
class AuthSignupRollbackIntegrationTest(
    authService: AuthService,
    loginEffects: LoginEffects,
    userRepository: UserRepository,
    boardRepository: BoardRepository,
) : IntegrationTest({
        Given("신규 사용자와 기본 보드를 만든 뒤 약관 조회가 실패할 때") {
            loginEffects.reset()
            val providerUserId = "rollback-${UUID.randomUUID()}"

            When("로그인하면") {
                val exception =
                    shouldThrow<IllegalStateException> {
                        authService.login(LoginCommand.Kakao(providerUserId))
                    }

                Then("사용자와 기본 보드를 롤백하고 token을 발급하거나 저장하지 않는다") {
                    exception.message shouldBe "약관 조회 실패"
                    userRepository
                        .findBySocialAccount(UserOAuthProvider.KAKAO, providerUserId)
                        .shouldBeNull()
                    boardRepository.findByUserId(loginEffects.userId!!) shouldHaveSize 0
                    loginEffects.tokenIssueCount shouldBe 0
                    loginEffects.tokenSaveCount shouldBe 0
                }
            }
        }

        Given("애플 최초 가입에서 authorization code 교환이 실패할 때") {
            loginEffects.reset()
            loginEffects.failTerms = false
            val providerUserId = "apple-rollback-${UUID.randomUUID()}"

            When("로그인하면") {
                val exception =
                    shouldThrow<UnauthorizedException> {
                        authService.login(
                            LoginCommand.Apple(
                                identityToken = providerUserId,
                                authorizationCode = "authorization-code",
                                rawNonce = "raw-nonce",
                            ),
                        )
                    }

                Then("트랜잭션 안에서 만든 사용자와 기본 보드를 함께 롤백해 유령 애플 계정을 남기지 않는다") {
                    exception.errorCode shouldBe AuthErrorCode.APPLE_CODE_EXCHANGE_FAILED
                    loginEffects.boardCountInTransaction shouldBe 1
                    userRepository
                        .findBySocialAccount(UserOAuthProvider.APPLE, providerUserId)
                        .shouldBeNull()
                    boardRepository.findByUserId(loginEffects.userId!!) shouldHaveSize 0
                    loginEffects.tokenIssueCount shouldBe 0
                    loginEffects.tokenSaveCount shouldBe 0
                }
            }
        }

        Given("약관 조회까지 성공하는 신규 로그인에서") {
            loginEffects.reset()
            loginEffects.failTerms = false
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

@Import(FailingBoardAuthTestConfig::class)
class AuthSignupBoardRollbackIntegrationTest(
    authUserPort: AuthUserPort,
    userRepository: UserRepository,
) : IntegrationTest({
        Given("신규 가입 중 기본 보드 생성이 실패할 때") {
            val providerUserId = "board-rollback-${UUID.randomUUID()}"

            When("로그인 트랜잭션 없이 가입 port를 직접 호출하면") {
                val exception =
                    shouldThrow<IllegalStateException> {
                        authUserPort.findOrCreate(
                            SocialProfile(
                                provider = OAuthProvider.KAKAO,
                                providerUserId = providerUserId,
                                email = "board-rollback@example.com",
                            ),
                        )
                    }

                Then("사용자 생성까지 함께 롤백해 유령 계정을 남기지 않는다") {
                    exception.message shouldBe "기본 보드 생성 실패"
                    userRepository
                        .findBySocialAccount(UserOAuthProvider.KAKAO, providerUserId)
                        .shouldBeNull()
                }
            }
        }
    })

@TestConfiguration(proxyBeanMethods = false)
class FailingBoardAuthTestConfig {
    @Bean
    @Primary
    fun failingBoardCommandService(
        boardRepository: BoardRepository,
        boardAccessService: BoardAccessService,
        drawingCommandService: BoardDrawingCommandService,
        analysisActivityPort: BoardAnalysisActivityPort,
        stickerCommandPort: BoardStickerCommandPort,
    ): BoardCommandService =
        FailingBoardCommandService(
            boardRepository,
            boardAccessService,
            drawingCommandService,
            analysisActivityPort,
            stickerCommandPort,
        )
}

open class FailingBoardCommandService(
    boardRepository: BoardRepository,
    boardAccessService: BoardAccessService,
    drawingCommandService: BoardDrawingCommandService,
    analysisActivityPort: BoardAnalysisActivityPort,
    stickerCommandPort: BoardStickerCommandPort,
) : BoardCommandService(boardRepository, boardAccessService, drawingCommandService, analysisActivityPort, stickerCommandPort) {
    override fun createDefault(userId: UUID): Board = error("기본 보드 생성 실패")
}

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
        AuthService(
            oauthClients = listOf(TrackingOAuthClient(loginEffects), ExchangeFailureAppleOAuthClient()),
            signupTransaction = signupTransaction,
            tokenProvider = TrackingTokenProvider(loginEffects),
            refreshTokenStore = TrackingRefreshTokenStore(loginEffects),
            authUserPort =
                AuthUserPort { profile ->
                    authUserPort.findOrCreate(profile).also {
                        loginEffects.userId = it.userId
                        loginEffects.boardCountInTransaction = boardRepository.findByUserId(it.userId).size
                    }
                },
            authTermsPort = TrackingTermsPort(loginEffects, authTermsPort),
            authActiveUserPort = authActiveUserPort,
        )
}

class LoginEffects {
    var userId: UUID? = null
    var failTerms: Boolean = true
    var providerCallInTransaction: Boolean = false
    var termsLookupInTransaction: Boolean = false
    var tokenIssueInTransaction: Boolean = false
    var tokenSaveInTransaction: Boolean = false
    var tokenIssueCount: Int = 0
    var tokenSaveCount: Int = 0
    var boardCountInTransaction: Int = 0

    fun reset() {
        userId = null
        failTerms = true
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
            authorizationCodeExchangeFailed = true,
        )

    override fun revoke(providerRefreshToken: String) = Unit
}

private class TrackingTermsPort(
    private val loginEffects: LoginEffects,
    private val delegate: AuthTermsPort,
) : AuthTermsPort {
    override fun findPendingTerms(userId: UUID): List<PendingTerm> {
        loginEffects.termsLookupInTransaction = isActualTransactionActive()
        check(!loginEffects.failTerms) { "약관 조회 실패" }
        return delegate.findPendingTerms(userId)
    }
}

private class TrackingTokenProvider(
    private val loginEffects: LoginEffects,
) : TokenProvider {
    override fun issue(userId: UUID): TokenPair {
        loginEffects.tokenIssueInTransaction = isActualTransactionActive()
        loginEffects.tokenIssueCount += 1
        return TokenPair("access-$userId", "refresh-$userId", 3_600)
    }

    override fun verifyAccessToken(accessToken: String): UUID = UUID.fromString(accessToken)
}

private class TrackingRefreshTokenStore(
    private val loginEffects: LoginEffects,
) : RefreshTokenStore {
    override fun save(
        userId: UUID,
        refreshToken: String,
    ) {
        loginEffects.tokenSaveInTransaction = isActualTransactionActive()
        loginEffects.tokenSaveCount += 1
    }

    override fun findUserId(refreshToken: String): UUID? = null

    override fun rotate(
        userId: UUID,
        currentRefreshToken: String,
        newRefreshToken: String,
    ): Boolean = false

    override fun delete(userId: UUID) = Unit
}
