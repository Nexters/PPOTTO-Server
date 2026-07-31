package com.github.nexters.ppotto.user.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID

class UserTest :
    BehaviorSpec({
        Given("활성 사용자가 있을 때") {
            val id = UUID.randomUUID()
            val user =
                User(
                    id = id,
                    provider = OAuthProvider.APPLE,
                    providerUserId = "apple-user",
                    email = "user@example.com",
                    providerRefreshToken = EncryptedProviderRefreshToken("encrypted"),
                    createdAt = Instant.parse("2026-07-01T00:00:00Z"),
                    updatedAt = Instant.parse("2026-07-01T00:00:00Z"),
                    deletedAt = null,
                )
            val withdrawnAt = Instant.parse("2026-07-30T00:00:00Z")

            When("탈퇴시키면") {
                val withdrawn = user.withdraw(withdrawnAt)

                Then("개인 정보를 익명화하고 삭제 시각을 기록한다") {
                    withdrawn.email shouldBe "deleted+$id@users.invalid"
                    withdrawn.providerRefreshToken shouldBe null
                    withdrawn.deletedAt shouldBe withdrawnAt
                    withdrawn.updatedAt shouldBe withdrawnAt
                    withdrawn.isActive shouldBe false
                }
            }
        }

        Given("이미 탈퇴한 사용자가 있을 때") {
            val at = Instant.parse("2026-07-30T00:00:00Z")
            val user =
                User(
                    id = UUID.randomUUID(),
                    provider = OAuthProvider.KAKAO,
                    providerUserId = "kakao-user",
                    email = "deleted@users.invalid",
                    providerRefreshToken = null,
                    createdAt = at,
                    updatedAt = at,
                    deletedAt = at,
                )

            When("다시 탈퇴시키면") {
                Then("상태 변경을 거부한다") {
                    shouldThrow<IllegalArgumentException> {
                        user.withdraw(at)
                    }
                }
            }
        }
    })
