package com.github.nexters.ppotto.user.application

import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.global.oauth.OAuthProvider
import com.github.nexters.ppotto.jooq.tables.references.USERS
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.runConcurrently
import com.github.nexters.ppotto.user.application.model.SocialUserCommand
import com.github.nexters.ppotto.user.application.port.SocialAccountRevoker
import com.github.nexters.ppotto.user.infrastructure.AesGcmProviderRefreshTokenCipher
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import com.github.nexters.ppotto.user.support.FakeSocialAccountRevoker
import com.github.nexters.ppotto.user.support.FakeUserSessionRevoker
import com.github.nexters.ppotto.user.support.Revocation
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.jooq.DSLContext
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID
import com.github.nexters.ppotto.jooq.enums.OauthProvider as JooqOAuthProvider

private val WITHDRAWN_AT = Instant.parse("2026-07-30T00:00:00Z")

private fun appleCommand(
    providerUserId: String,
    email: String? = "apple@example.com",
    name: String? = "애플사용자",
    providerRefreshToken: String? = null,
) = SocialUserCommand(
    provider = OAuthProvider.APPLE,
    providerUserId = providerUserId,
    email = email,
    name = name,
    providerRefreshToken = providerRefreshToken,
)

class UserServiceTest(
    userService: UserService,
    userRepository: UserRepository,
    tokenCipher: AesGcmProviderRefreshTokenCipher,
    revoker: FakeSocialAccountRevoker,
    sessionRevoker: FakeUserSessionRevoker,
    transactionTemplate: TransactionTemplate,
    dslContext: DSLContext,
) : IntegrationTest({
        Given("가입한 적 없는 Apple 소셜 계정이 있을 때") {
            val command =
                appleCommand(
                    providerUserId = "apple-${UUID.randomUUID()}",
                    email = "first@example.com",
                    providerRefreshToken = "first-refresh-token",
                )

            When("사용자를 조회하거나 생성하면") {
                val result = userService.findOrCreate(command)!!

                Then("새 사용자를 생성한다") {
                    result.isNewUser shouldBe true
                    result.user.provider shouldBe OAuthProvider.APPLE
                    result.user.providerUserId shouldBe command.providerUserId
                    result.user.name shouldBe "애플사용자"
                }
            }
        }

        Given("이미 가입한 Apple 소셜 계정이 있을 때") {
            val providerUserId = "apple-${UUID.randomUUID()}"
            val first =
                userService.findOrCreate(
                    appleCommand(
                        providerUserId = providerUserId,
                        email = "first@example.com",
                        providerRefreshToken = "first-refresh-token",
                    ),
                )!!

            When("이름 없이 다시 로그인하면") {
                val second =
                    userService.findOrCreate(
                        appleCommand(
                            providerUserId = providerUserId,
                            email = "changed@example.com",
                            name = null,
                            providerRefreshToken = "second-refresh-token",
                        ),
                    )!!

                Then("기존 사용자의 이메일을 갱신하고 이름은 유지한다") {
                    second.isNewUser shouldBe false
                    second.user.id shouldBe first.user.id
                    second.user.email shouldBe "changed@example.com"
                    second.user.name shouldBe "애플사용자"
                }
            }

            When("이름과 이메일 없이 다시 로그인하면") {
                val second =
                    userService.findOrCreate(
                        appleCommand(
                            providerUserId = providerUserId,
                            email = null,
                            name = null,
                            providerRefreshToken = "relogin-refresh-token",
                        ),
                    )!!

                Then("저장된 이메일과 이름을 유지한 채 로그인한다") {
                    second.isNewUser shouldBe false
                    second.user.id shouldBe first.user.id
                    second.user.email shouldBe first.user.email
                    second.user.name shouldBe "애플사용자"
                }
            }
        }

        Given("가입한 적 없는 소셜 계정이 이메일 없이 로그인할 때") {
            val command = appleCommand(providerUserId = "emailless-${UUID.randomUUID()}", email = null)

            When("사용자를 조회하거나 생성하면") {
                val result = userService.findOrCreate(command)

                Then("사용자를 생성하지 않고 null을 반환한다") {
                    result.shouldBeNull()
                    userRepository
                        .findBySocialAccount(OAuthProvider.APPLE, command.providerUserId)
                        .shouldBeNull()
                }
            }
        }

        Given("가입한 적 없는 소셜 계정이 이름 없이 로그인할 때") {
            val command = appleCommand(providerUserId = "nameless-${UUID.randomUUID()}", name = null)

            When("사용자를 조회하거나 생성하면") {
                val result = userService.findOrCreate(command)

                Then("사용자를 생성하지 않고 null을 반환한다") {
                    result.shouldBeNull()
                    userRepository
                        .findBySocialAccount(OAuthProvider.APPLE, command.providerUserId)
                        .shouldBeNull()
                }
            }
        }

        Given("같은 소셜 계정으로 여러 가입 요청이 동시에 시작될 때") {
            val providerUserId = "concurrent-${UUID.randomUUID()}"
            val command =
                SocialUserCommand(
                    provider = OAuthProvider.KAKAO,
                    providerUserId = providerUserId,
                    email = "concurrent@example.com",
                    name = "동시가입사용자",
                    providerRefreshToken = null,
                )
            val requestCount = 8

            When("사용자를 동시에 조회하거나 생성하면") {
                val results =
                    runConcurrently(requestCount) { userService.findOrCreate(command) }
                        .map { it.getOrThrow()!! }

                Then("한 사용자만 저장하고 한 요청만 신규 가입으로 반환한다") {
                    results
                        .map { it.user.id }
                        .distinct()
                        .size shouldBe 1
                    results.count { it.isNewUser } shouldBe 1
                    dslContext.fetchCount(
                        USERS,
                        USERS.PROVIDER
                            .eq(JooqOAuthProvider.KAKAO)
                            .and(USERS.PROVIDER_USER_ID.eq(providerUserId))
                            .and(USERS.DELETED_AT.isNull),
                    ) shouldBe 1
                }
            }
        }

        Given("제공자 refresh token을 가진 사용자가 있을 때") {
            val result =
                userService.findOrCreate(
                    appleCommand(
                        providerUserId = "withdraw-apple-${UUID.randomUUID()}",
                        email = "withdraw@example.com",
                        name = "탈퇴예정사용자",
                        providerRefreshToken = "revoke-me",
                    ),
                )!!

            When("회원 탈퇴를 처리하면") {
                userService.withdraw(result.user.id, WITHDRAWN_AT)

                Then("제공자 계정을 해지한다") {
                    revoker.revocations shouldContainExactly listOf(Revocation(OAuthProvider.APPLE, "revoke-me"))
                }

                Then("트랜잭션이 없으므로 반환 시점에 이미 서비스 세션을 해지해 둔다") {
                    sessionRevoker.revokedUserIds shouldContainExactly listOf(result.user.id)
                }

                Then("활성 사용자 조회에서 제외한다") {
                    userRepository.findById(result.user.id).shouldBeNull()
                }
            }
        }

        Given("제공자 refresh token이 없는 사용자가 있을 때") {
            val result =
                userService.findOrCreate(
                    appleCommand(
                        providerUserId = "tokenless-${UUID.randomUUID()}",
                        email = "tokenless@example.com",
                        name = "토큰없는사용자",
                    ),
                )!!

            When("회원 탈퇴를 처리하면") {
                userService.withdraw(result.user.id, WITHDRAWN_AT)

                Then("제공자 계정 해지를 건너뛰고 탈퇴를 완료한다") {
                    revoker.revocations.shouldBeEmpty()
                    userRepository.findById(result.user.id).shouldBeNull()
                }
            }
        }

        Given("제공자 refresh token 없이 탈퇴한 애플 계정이 있을 때") {
            val providerUserId = "tokenless-rejoin-${UUID.randomUUID()}"
            val withdrawn =
                userService.findOrCreate(
                    appleCommand(
                        providerUserId = providerUserId,
                        email = "tokenless@example.com",
                        name = "토큰없는사용자",
                    ),
                )!!
            userService.withdraw(withdrawn.user.id, WITHDRAWN_AT)

            When("이름과 함께 다시 가입하면") {
                val rejoined =
                    userService.findOrCreate(
                        appleCommand(
                            providerUserId = providerUserId,
                            email = "rejoin@example.com",
                            name = "재가입사용자",
                            providerRefreshToken = "rejoin-refresh-token",
                        ),
                    )!!

                Then("탈퇴한 계정과 별개인 새 사용자로 가입한다") {
                    rejoined.isNewUser shouldBe true
                    rejoined.user.id shouldNotBe withdrawn.user.id
                    rejoined.user.name shouldBe "재가입사용자"
                    rejoined.user.email shouldBe "rejoin@example.com"
                }
            }

            When("이름 없이 다시 가입하면") {
                val rejoined =
                    userService.findOrCreate(
                        appleCommand(
                            providerUserId = providerUserId,
                            email = "rejoin@example.com",
                            name = null,
                            providerRefreshToken = "rejoin-refresh-token",
                        ),
                    )

                Then("탈퇴한 계정을 되살리지 않고 null을 반환한다") {
                    rejoined.shouldBeNull()
                }
            }
        }

        Given("제공자 계정 해지가 실패하는 사용자가 있을 때") {
            val result =
                userService.findOrCreate(
                    appleCommand(
                        providerUserId = "revoke-fail-${UUID.randomUUID()}",
                        email = "revokefail@example.com",
                        name = "해지실패사용자",
                        providerRefreshToken = "revoke-me",
                    ),
                )!!
            revoker.failure = IllegalStateException("소셜 계정 해지에 실패했습니다.")

            When("회원 탈퇴를 처리하면") {
                val exception =
                    shouldThrow<IllegalStateException> { userService.withdraw(result.user.id, WITHDRAWN_AT) }

                Then("해지 실패를 그대로 전파한다") {
                    exception.message shouldBe "소셜 계정 해지에 실패했습니다."
                }

                Then("아무것도 쓰지 않고 사용자를 활성 상태로 남긴다") {
                    userRepository.findById(result.user.id).shouldNotBeNull()
                }

                Then("서비스 세션도 해지하지 않는다") {
                    sessionRevoker.revokedUserIds.shouldBeEmpty()
                }
            }
        }

        Given("해지 시점의 사용자 행 상태를 관찰하는 해지 어댑터가 있을 때") {
            val user =
                userService
                    .findOrCreate(
                        appleCommand(
                            providerUserId = "revoke-order-${UUID.randomUUID()}",
                            email = "revokeorder@example.com",
                            name = "해지순서사용자",
                            providerRefreshToken = "revoke-me",
                        ),
                    )!!
                    .user
            var activeWhenRevoked: Boolean? = null
            val observingService =
                UserService(
                    userRepository = userRepository,
                    tokenCipher = tokenCipher,
                    socialAccountRevoker =
                        SocialAccountRevoker { _, _ ->
                            activeWhenRevoked = userRepository.findById(user.id) != null
                        },
                    userSessionRevoker = sessionRevoker,
                )

            When("회원 탈퇴를 처리하면") {
                observingService.withdraw(user.id, WITHDRAWN_AT)

                Then("제공자 해지 시점에는 사용자 행이 아직 활성이다") {
                    activeWhenRevoked shouldBe true
                }

                Then("해지 뒤에야 사용자 행을 탈퇴로 쓴다") {
                    userRepository.findById(user.id).shouldBeNull()
                }
            }
        }

        Given("호출자가 탈퇴를 트랜잭션으로 감쌌을 때") {
            val user =
                userService
                    .findOrCreate(
                        appleCommand(
                            providerUserId = "withdraw-tx-${UUID.randomUUID()}",
                            email = "withdrawtx@example.com",
                            name = "트랜잭션사용자",
                            providerRefreshToken = "revoke-me",
                        ),
                    )!!
                    .user

            When("트랜잭션이 커밋되면") {
                val revokedInsideTransaction =
                    transactionTemplate.execute {
                        userService.withdraw(user.id, WITHDRAWN_AT)
                        sessionRevoker.revokedUserIds.toList()
                    }!!

                Then("트랜잭션 안에서는 아직 세션을 해지하지 않는다") {
                    revokedInsideTransaction.shouldBeEmpty()
                }

                Then("커밋 후에 세션을 해지한다") {
                    sessionRevoker.revokedUserIds shouldContainExactly listOf(user.id)
                }
            }

            When("트랜잭션이 롤백되면") {
                transactionTemplate.executeWithoutResult { status ->
                    userService.withdraw(user.id, WITHDRAWN_AT)
                    status.setRollbackOnly()
                }

                Then("세션을 해지하지 않는다") {
                    sessionRevoker.revokedUserIds.shouldBeEmpty()
                }

                Then("사용자를 활성 상태로 남긴다") {
                    userRepository.findById(user.id).shouldNotBeNull()
                }
            }
        }

        Given("같은 사용자에 대해 탈퇴 요청이 동시에 시작될 때") {
            val user =
                userService
                    .findOrCreate(
                        appleCommand(
                            providerUserId = "withdraw-race-${UUID.randomUUID()}",
                            email = "withdrawrace@example.com",
                            name = "동시탈퇴사용자",
                            providerRefreshToken = "revoke-me",
                        ),
                    )!!
                    .user

            When("두 요청이 동시에 탈퇴를 처리하면") {
                val results = runConcurrently(2) { userService.withdraw(user.id, WITHDRAWN_AT) }

                Then("한 요청만 성공한다") {
                    results.count { it.isSuccess } shouldBe 1
                }

                Then("나머지 한 요청은 USER-001 오류를 받는다") {
                    val failure =
                        results
                            .mapNotNull { it.exceptionOrNull() }
                            .single()
                            .shouldBeInstanceOf<NotFoundException>()
                    failure.errorCode.code shouldBe "USER-001"
                }

                Then("사용자는 한 번만 탈퇴 처리된다") {
                    userRepository.findById(user.id).shouldBeNull()
                    dslContext.fetchCount(
                        USERS,
                        USERS.ID
                            .eq(user.id)
                            .and(USERS.DELETED_AT.isNotNull),
                    ) shouldBe 1
                }
            }
        }
    })
