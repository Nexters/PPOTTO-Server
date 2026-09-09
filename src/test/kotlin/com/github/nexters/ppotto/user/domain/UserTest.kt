package com.github.nexters.ppotto.user.domain

import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.oauth.OAuthProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID

class UserTest :
    BehaviorSpec({
        Given("활성 사용자가 있을 때") {
            val id = UserId(UUID.randomUUID())
            val user =
                User(
                    id = id,
                    provider = OAuthProvider.APPLE,
                    providerUserId = "apple-user",
                    email = "user@example.com",
                    name = "애플사용자",
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
                    withdrawn.name shouldBe "탈퇴한 사용자"
                    withdrawn.providerRefreshToken shouldBe null
                    withdrawn.deletedAt shouldBe withdrawnAt
                    withdrawn.isActive shouldBe false
                }

                Then("updatedAt은 DB 트리거가 정하므로 도메인에서 바꾸지 않는다") {
                    withdrawn.updatedAt shouldBe user.updatedAt
                }
            }
        }

        Given("이미 탈퇴한 사용자가 있을 때") {
            val at = Instant.parse("2026-07-30T00:00:00Z")
            val user =
                User(
                    id = UserId(UUID.randomUUID()),
                    provider = OAuthProvider.KAKAO,
                    providerUserId = "kakao-user",
                    email = "deleted@users.invalid",
                    name = "탈퇴한 사용자",
                    providerRefreshToken = null,
                    createdAt = at,
                    updatedAt = at,
                    deletedAt = at,
                )

            When("다시 탈퇴시키면") {
                val exception = shouldThrow<IllegalArgumentException> { user.withdraw(at) }

                Then("이미 탈퇴한 사용자라며 상태 변경을 거부한다") {
                    exception.message shouldBe "이미 탈퇴한 사용자입니다."
                }
            }
        }
    })
