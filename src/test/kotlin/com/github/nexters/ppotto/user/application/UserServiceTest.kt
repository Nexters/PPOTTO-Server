package com.github.nexters.ppotto.user.application

import com.github.nexters.ppotto.jooq.tables.references.USERS
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.runConcurrently
import com.github.nexters.ppotto.user.domain.OAuthProvider
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import com.github.nexters.ppotto.user.support.FakeSocialAccountRevoker
import com.github.nexters.ppotto.user.support.FakeUserSessionRevoker
import com.github.nexters.ppotto.user.support.Revocation
import com.github.nexters.ppotto.user.support.UserTestConfig
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import org.springframework.context.annotation.Import
import java.time.Instant
import java.util.UUID
import com.github.nexters.ppotto.jooq.enums.OauthProvider as JooqOAuthProvider

@Import(UserTestConfig::class)
class UserServiceTest(
    userService: UserService,
    userRepository: UserRepository,
    revoker: FakeSocialAccountRevoker,
    sessionRevoker: FakeUserSessionRevoker,
    dslContext: DSLContext,
) : IntegrationTest({
        Given("처음 로그인한 Apple 소셜 계정이 있을 때") {
            revoker.clear()
            val providerUserId = "apple-${UUID.randomUUID()}"
            val firstCommand =
                SocialUserCommand(
                    provider = OAuthProvider.APPLE,
                    providerUserId = providerUserId,
                    email = "first@example.com",
                    name = "애플사용자",
                    providerRefreshToken = "first-refresh-token",
                )

            When("사용자를 조회하거나 생성하면") {
                val result = userService.findOrCreate(firstCommand)!!

                Then("새 사용자를 생성한다") {
                    result.isNewUser shouldBe true
                    result.user.provider shouldBe OAuthProvider.APPLE
                    result.user.providerUserId shouldBe providerUserId
                    result.user.name shouldBe "애플사용자"
                }
            }

            When("같은 소셜 계정으로 이름 없이 다시 로그인하면") {
                val first = userService.findOrCreate(firstCommand)!!
                val second =
                    userService.findOrCreate(
                        firstCommand.copy(
                            email = "changed@example.com",
                            name = null,
                            providerRefreshToken = "second-refresh-token",
                        ),
                    )!!

                Then("기존 사용자의 프로필과 제공자 토큰을 갱신하고 이름은 유지한다") {
                    second.isNewUser shouldBe false
                    second.user.id shouldBe first.user.id
                    second.user.email shouldBe "changed@example.com"
                    second.user.name shouldBe "애플사용자"
                }
            }

            When("같은 소셜 계정으로 이름과 이메일 없이 다시 로그인하면") {
                val first = userService.findOrCreate(firstCommand)!!
                val second =
                    userService.findOrCreate(
                        firstCommand.copy(
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
            val command =
                SocialUserCommand(
                    provider = OAuthProvider.APPLE,
                    providerUserId = "emailless-${UUID.randomUUID()}",
                    email = null,
                    name = "이메일없는사용자",
                    providerRefreshToken = null,
                )

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
            val command =
                SocialUserCommand(
                    provider = OAuthProvider.APPLE,
                    providerUserId = "nameless-${UUID.randomUUID()}",
                    email = "nameless@example.com",
                    name = null,
                    providerRefreshToken = null,
                )

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
            revoker.clear()
            sessionRevoker.clear()
            val result =
                userService.findOrCreate(
                    SocialUserCommand(
                        provider = OAuthProvider.APPLE,
                        providerUserId = "withdraw-apple-${UUID.randomUUID()}",
                        email = "withdraw@example.com",
                        name = "탈퇴예정사용자",
                        providerRefreshToken = "revoke-me",
                    ),
                )!!
            val withdrawnAt = Instant.parse("2026-07-30T00:00:00Z")

            When("회원 탈퇴를 처리하면") {
                userService.withdraw(result.user.id, withdrawnAt)

                Then("제공자 계정을 해지하고 활성 사용자 조회에서 제외한다") {
                    revoker.revocations shouldContainExactly listOf(Revocation(OAuthProvider.APPLE, "revoke-me"))
                    sessionRevoker.revokedUserIds shouldContainExactly listOf(result.user.id)
                    userRepository.findById(result.user.id).shouldBeNull()
                }
            }
        }
    })
