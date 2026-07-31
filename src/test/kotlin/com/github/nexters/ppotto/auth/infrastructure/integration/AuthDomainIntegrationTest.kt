package com.github.nexters.ppotto.auth.infrastructure.integration

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
import com.github.nexters.ppotto.auth.domain.TokenPair
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.jooq.tables.references.TERMS
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
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
) : IntegrationTest({
        Given("production auth 도메인 adapter가 연결된 상태에서") {
            Then("AuthService bean을 생성한다") {
                authService.javaClass.simpleName
                    .isNotBlank() shouldBe true
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

@Import(FailingTermsAuthTestConfig::class)
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
    })

@TestConfiguration(proxyBeanMethods = false)
class FailingTermsAuthTestConfig {
    @Bean
    fun loginEffects(): LoginEffects = LoginEffects()

    @Bean
    @Primary
    fun failingTermsAuthService(
        authActiveUserPort: AuthActiveUserPort,
        authUserPort: AuthUserPort,
        loginEffects: LoginEffects,
    ): AuthService =
        AuthService(
            oauthClients = listOf(FailingTermsOAuthClient()),
            tokenProvider = TrackingTokenProvider(loginEffects),
            refreshTokenStore = TrackingRefreshTokenStore(loginEffects),
            authUserPort =
                AuthUserPort { profile ->
                    authUserPort.findOrCreate(profile).also {
                        loginEffects.userId = it.userId
                    }
                },
            authTermsPort = AuthTermsPort { error("약관 조회 실패") },
            authActiveUserPort = authActiveUserPort,
        )
}

class LoginEffects {
    var userId: UUID? = null
    var tokenIssueCount: Int = 0
    var tokenSaveCount: Int = 0

    fun reset() {
        userId = null
        tokenIssueCount = 0
        tokenSaveCount = 0
    }
}

private class FailingTermsOAuthClient : OAuthClient {
    override val provider = OAuthProvider.KAKAO

    override fun authenticate(command: LoginCommand): SocialProfile {
        val kakao = command as LoginCommand.Kakao
        return SocialProfile(
            provider = provider,
            providerUserId = kakao.accessToken,
            email = "rollback@example.com",
        )
    }

    override fun revoke(providerRefreshToken: String) = Unit
}

private class TrackingTokenProvider(
    private val loginEffects: LoginEffects,
) : TokenProvider {
    override fun issue(userId: UUID): TokenPair {
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
