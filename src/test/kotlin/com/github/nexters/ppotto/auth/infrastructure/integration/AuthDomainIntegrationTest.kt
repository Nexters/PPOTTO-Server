package com.github.nexters.ppotto.auth.infrastructure.integration

import com.github.nexters.ppotto.auth.application.AuthService.Companion.SIGNUP_TRANSACTION
import com.github.nexters.ppotto.auth.application.port.AuthTermsPort
import com.github.nexters.ppotto.auth.application.port.AuthUserPort
import com.github.nexters.ppotto.auth.config.AuthTransactionConfig.Companion.SIGNUP_TRANSACTION_TIMEOUT_SECONDS
import com.github.nexters.ppotto.auth.domain.SocialProfile
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.oauth.OAuthProvider
import com.github.nexters.ppotto.jooq.tables.references.TERMS
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.runConcurrently
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import org.jooq.DSLContext
import org.springframework.context.ApplicationContext
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID

class AuthDomainIntegrationTest(
    authUserPort: AuthUserPort,
    authTermsPort: AuthTermsPort,
    userRepository: UserRepository,
    boardRepository: BoardRepository,
    dslContext: DSLContext,
    applicationContext: ApplicationContext,
) : IntegrationTest({
        Given("production auth 도메인 adapter가 연결된 상태에서") {
            When("가입 전용 트랜잭션 bean을 조회하면") {
                val signupTransaction = applicationContext.getBean(SIGNUP_TRANSACTION, TransactionTemplate::class.java)

                Then("가입 구간은 timeout이 제한된 전용 트랜잭션을 사용한다") {
                    signupTransaction.timeout shouldBe SIGNUP_TRANSACTION_TIMEOUT_SECONDS
                }

                Then("공유 template과 다른 인스턴스다") {
                    signupTransaction shouldNotBeSameInstanceAs
                        applicationContext.getBean("transactionTemplate", TransactionTemplate::class.java)
                }
            }
        }

        Given("같은 Apple 소셜 계정으로 두 번 로그인할 때") {
            val providerUserId = "existing-${UUID.randomUUID()}"
            val profile =
                SocialProfile(
                    provider = OAuthProvider.APPLE,
                    providerUserId = providerUserId,
                    email = "existing@example.com",
                    name = "애플사용자",
                    providerRefreshToken = "provider-refresh-token",
                )

            When("사용자를 두 번 조회하거나 생성하면") {
                val first = authUserPort.findOrCreate(profile)!!
                val second = authUserPort.findOrCreate(profile)!!

                Then("기존 사용자에게 기본 보드를 중복 생성하지 않는다") {
                    first.isNewUser shouldBe true
                    second.isNewUser shouldBe false
                    second.userId shouldBe first.userId
                    userRepository
                        .findBySocialAccount(OAuthProvider.APPLE, providerUserId)
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
                    name = "동시가입사용자",
                )

            When("가입 port를 동시에 호출하면") {
                val users =
                    runConcurrently(6) { authUserPort.findOrCreate(profile) }
                        .map { it.getOrThrow()!! }

                Then("계정과 기본 보드를 각각 한 번만 생성한다") {
                    users.map { it.userId }.distinct() shouldHaveSize 1
                    users.count { it.isNewUser } shouldBe 1
                    boardRepository.findByUserId(
                        users
                            .first()
                            .userId,
                    ) shouldHaveSize 1
                    userRepository
                        .findBySocialAccount(OAuthProvider.KAKAO, providerUserId)
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
                        name = "약관사용자",
                    ),
                )!!
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
