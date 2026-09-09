package com.github.nexters.ppotto.user.application

import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.jooq.tables.references.USERS
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.application.port.WithdrawnUserAnalysisDeletionPort
import com.github.nexters.ppotto.user.application.port.WithdrawnUserBoardDeletionPort
import com.github.nexters.ppotto.user.application.port.WithdrawnUserStickerDeletionPort
import com.github.nexters.ppotto.user.application.port.WithdrawnUserTermAgreementDeletionPort
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import java.time.Instant

private class RecordingDeletionPorts(
    private val failingUserIds: Set<UserId> = emptySet(),
) {
    val deletionOrder = mutableListOf<String>()

    val boardPort =
        object : WithdrawnUserBoardDeletionPort {
            override fun findAllBoardIds(userId: UserId): List<BoardId> {
                if (userId in failingUserIds) {
                    error("연관 데이터 삭제 실패")
                }
                deletionOrder += "board-ids"
                return emptyList()
            }

            override fun deleteAllByUserId(userId: UserId) {
                deletionOrder += "board"
            }
        }

    val stickerPort = WithdrawnUserStickerDeletionPort { deletionOrder += "sticker" }

    val analysisPort = WithdrawnUserAnalysisDeletionPort { deletionOrder += "analysis" }

    val termAgreementPort = WithdrawnUserTermAgreementDeletionPort { deletionOrder += "term-agreement" }
}

private fun cleanupServiceWith(
    userRepository: UserRepository,
    ports: RecordingDeletionPorts,
) = WithdrawnUserCleanupService(
    userRepository = userRepository,
    boardDeletionPort = ports.boardPort,
    stickerDeletionPort = ports.stickerPort,
    analysisDeletionPort = ports.analysisPort,
    termAgreementDeletionPort = ports.termAgreementPort,
)

class WithdrawnUserCleanupServiceTest(
    userRepository: UserRepository,
    dslContext: DSLContext,
) : IntegrationTest({
        Given("유예 기준보다 오래된 탈퇴 사용자가 있을 때") {
            val ports = RecordingDeletionPorts()
            val cleanupService = cleanupServiceWith(userRepository, ports)
            val withdrawn =
                userRepository
                    .saveTestUser()
                    .let { userRepository.withdraw(it.withdraw(Instant.parse("2026-07-01T00:00:00Z")))!! }

            When("탈퇴 사용자 정리 배치를 실행하면") {
                val result =
                    cleanupService.cleanup(
                        deletedBefore = Instant.parse("2026-07-02T00:00:00Z"),
                        batchSize = 10,
                    )

                Then("연관 데이터를 먼저 삭제한 뒤 사용자 행을 하드 삭제한다") {
                    result.attempted shouldBe 1
                    result.deletedUserIds shouldContainExactly listOf(withdrawn.id)
                    dslContext.fetchExists(USERS, USERS.ID.eq(withdrawn.id)) shouldBe false
                }

                Then("외래키 순서대로 스티커, 분석, 보드, 약관 동의를 지운다") {
                    ports.deletionOrder shouldContainExactly
                        listOf("board-ids", "sticker", "analysis", "board", "term-agreement")
                }
            }
        }

        Given("연관 데이터 삭제가 실패하는 탈퇴 사용자가 있을 때") {
            val withdrawn =
                userRepository
                    .saveTestUser()
                    .let { userRepository.withdraw(it.withdraw(Instant.parse("2026-07-03T00:00:00Z")))!! }
            val cleanupService =
                cleanupServiceWith(userRepository, RecordingDeletionPorts(failingUserIds = setOf(withdrawn.id)))

            When("탈퇴 사용자 정리 배치를 실행하면") {
                val exception =
                    shouldThrow<IllegalStateException> {
                        cleanupService.cleanup(
                            deletedBefore = Instant.parse("2026-07-04T00:00:00Z"),
                            batchSize = 10,
                        )
                    }

                Then("실패를 그대로 전파한다") {
                    exception.message shouldBe "연관 데이터 삭제 실패"
                }

                Then("사용자 행을 보존한다") {
                    dslContext.fetchExists(USERS, USERS.ID.eq(withdrawn.id)) shouldBe true
                }
            }
        }

        Given("배치 크기 상한을 넘긴 요청이 있을 때") {
            val cleanupService = cleanupServiceWith(userRepository, RecordingDeletionPorts())

            When("정리 배치를 실행하면") {
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        cleanupService.cleanup(
                            deletedBefore = Instant.parse("2026-07-04T00:00:00Z"),
                            batchSize = (MAX_CLEANUP_BATCH_SIZE + 1).toInt(),
                        )
                    }

                Then("배치 크기 상한을 알리며 거부한다") {
                    exception.message shouldBe "정리 배치 크기는 1 이상 $MAX_CLEANUP_BATCH_SIZE 이하여야 합니다."
                }
            }
        }
    })
