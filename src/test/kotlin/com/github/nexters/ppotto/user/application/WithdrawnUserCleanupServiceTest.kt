package com.github.nexters.ppotto.user.application

import com.github.nexters.ppotto.jooq.tables.references.USERS
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.user.domain.OAuthProvider
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import com.github.nexters.ppotto.user.support.FakeWithdrawnUserDataDeletionPort
import com.github.nexters.ppotto.user.support.UserTestConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import org.springframework.context.annotation.Import
import java.time.Instant
import java.util.UUID

@Import(UserTestConfig::class)
class WithdrawnUserCleanupServiceTest(
    cleanupService: WithdrawnUserCleanupService,
    userRepository: UserRepository,
    dataDeletionPort: FakeWithdrawnUserDataDeletionPort,
    dslContext: DSLContext,
) : IntegrationTest({
        Given("유예 기준보다 오래된 탈퇴 사용자들이 있을 때") {
            dataDeletionPort.clear()
            val withdrawnAt = Instant.parse("2026-07-01T00:00:00Z")
            val withdrawn =
                userRepository
                    .save(
                        provider = OAuthProvider.KAKAO,
                        providerUserId = "cleanup-${UUID.randomUUID()}",
                        email = "cleanup@example.com",
                    ).let { userRepository.withdraw(it.withdraw(withdrawnAt))!! }

            When("탈퇴 사용자 정리 배치를 실행하면") {
                val result =
                    cleanupService.cleanup(
                        deletedBefore = Instant.parse("2026-07-02T00:00:00Z"),
                        batchSize = 10,
                    )

                Then("연관 데이터를 먼저 삭제한 뒤 사용자 행을 하드 삭제한다") {
                    result.attempted shouldBe 1
                    result.deletedUserIds shouldContainExactly listOf(withdrawn.id)
                    dataDeletionPort.deletedUserIds shouldContainExactly listOf(withdrawn.id)
                    dslContext.fetchExists(USERS, USERS.ID.eq(withdrawn.id)) shouldBe false
                }
            }
        }

        Given("연관 데이터 삭제가 실패하는 탈퇴 사용자가 있을 때") {
            dataDeletionPort.clear()
            val withdrawn =
                userRepository
                    .save(
                        provider = OAuthProvider.APPLE,
                        providerUserId = "cleanup-failure-${UUID.randomUUID()}",
                        email = "failure@example.com",
                    ).let { userRepository.withdraw(it.withdraw(Instant.parse("2026-07-03T00:00:00Z")))!! }
            dataDeletionPort.failingUserIds += withdrawn.id

            When("탈퇴 사용자 정리 배치를 실행하면") {
                Then("실패를 전파하고 사용자 행을 보존한다") {
                    shouldThrow<IllegalStateException> {
                        cleanupService.cleanup(
                            deletedBefore = Instant.parse("2026-07-04T00:00:00Z"),
                            batchSize = 10,
                        )
                    }
                    dslContext.fetchExists(USERS, USERS.ID.eq(withdrawn.id)) shouldBe true
                }
            }
        }
    })
