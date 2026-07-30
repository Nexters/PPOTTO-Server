package com.github.nexters.ppotto.user.application

import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.user.domain.OAuthProvider
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import com.github.nexters.ppotto.user.support.FakeSocialAccountRevoker
import com.github.nexters.ppotto.user.support.Revocation
import com.github.nexters.ppotto.user.support.UserTestConfig
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.context.annotation.Import
import java.time.Instant
import java.util.UUID

@Import(UserTestConfig::class)
class UserServiceTest(
    userService: UserService,
    userRepository: UserRepository,
    revoker: FakeSocialAccountRevoker,
) : IntegrationTest({
        Given("처음 로그인한 Apple 소셜 계정이 있을 때") {
            revoker.clear()
            val providerUserId = "apple-${UUID.randomUUID()}"
            val firstCommand =
                SocialUserCommand(
                    provider = OAuthProvider.APPLE,
                    providerUserId = providerUserId,
                    email = "first@example.com",
                    providerRefreshToken = "first-refresh-token",
                )

            When("사용자를 조회하거나 생성하면") {
                val result = userService.findOrCreate(firstCommand)

                Then("새 사용자를 생성한다") {
                    result.isNewUser shouldBe true
                    result.user.provider shouldBe OAuthProvider.APPLE
                    result.user.providerUserId shouldBe providerUserId
                }
            }

            When("같은 소셜 계정으로 다시 로그인하면") {
                val first = userService.findOrCreate(firstCommand)
                val second =
                    userService.findOrCreate(
                        firstCommand.copy(
                            email = "changed@example.com",
                            providerRefreshToken = "second-refresh-token",
                        ),
                    )

                Then("기존 사용자의 프로필과 제공자 토큰을 갱신한다") {
                    second.isNewUser shouldBe false
                    second.user.id shouldBe first.user.id
                    second.user.email shouldBe "changed@example.com"
                }
            }
        }

        Given("제공자 refresh token을 가진 사용자가 있을 때") {
            revoker.clear()
            val result =
                userService.findOrCreate(
                    SocialUserCommand(
                        provider = OAuthProvider.APPLE,
                        providerUserId = "withdraw-apple-${UUID.randomUUID()}",
                        email = "withdraw@example.com",
                        providerRefreshToken = "revoke-me",
                    ),
                )
            val withdrawnAt = Instant.parse("2026-07-30T00:00:00Z")

            When("회원 탈퇴를 처리하면") {
                userService.withdraw(result.user.id, withdrawnAt)

                Then("제공자 계정을 해지하고 활성 사용자 조회에서 제외한다") {
                    revoker.revocations shouldContainExactly listOf(Revocation(OAuthProvider.APPLE, "revoke-me"))
                    userRepository.findById(result.user.id).shouldBeNull()
                }
            }
        }
    })
