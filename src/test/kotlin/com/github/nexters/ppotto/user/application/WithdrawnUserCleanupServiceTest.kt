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

private const val BATCH_SIZE_RANGE_MESSAGE = "정리 배치 크기는 1 이상 1000 이하여야 합니다."

/**
 * 프로덕션 어댑터 빈을 그대로 호출하면서 호출 순서만 기록한다.
 * 테스트가 직접 만든 포트를 핀하면 어댑터를 갈아끼워도 순서 단언이 통과하므로, 주입된 빈을 감싸기만 한다.
 */
private class RecordingDeletionPorts(
    private val board: WithdrawnUserBoardDeletionPort,
    private val sticker: WithdrawnUserStickerDeletionPort,
    private val analysis: WithdrawnUserAnalysisDeletionPort,
    private val termAgreement: WithdrawnUserTermAgreementDeletionPort,
) {
    val deletionOrder = mutableListOf<String>()

    val boardPort =
        object : WithdrawnUserBoardDeletionPort {
            override fun findAllBoardIds(userId: UserId): List<BoardId> {
                deletionOrder += "board-ids"
                return board.findAllBoardIds(userId)
            }

            override fun deleteAllByUserId(userId: UserId) {
                deletionOrder += "board"
                board.deleteAllByUserId(userId)
            }
        }

    val stickerPort =
        WithdrawnUserStickerDeletionPort { boardIds ->
            deletionOrder += "sticker"
            sticker.deleteAllByBoardIds(boardIds)
        }

    val analysisPort =
        WithdrawnUserAnalysisDeletionPort { userId ->
            deletionOrder += "analysis"
            analysis.deleteAllByUserId(userId)
        }

    val termAgreementPort =
        WithdrawnUserTermAgreementDeletionPort { userId ->
            deletionOrder += "term-agreement"
            termAgreement.deleteAllByUserId(userId)
        }
}

class WithdrawnUserCleanupServiceTest(
    userRepository: UserRepository,
    boardDeletionPort: WithdrawnUserBoardDeletionPort,
    stickerDeletionPort: WithdrawnUserStickerDeletionPort,
    analysisDeletionPort: WithdrawnUserAnalysisDeletionPort,
    termAgreementDeletionPort: WithdrawnUserTermAgreementDeletionPort,
    dslContext: DSLContext,
) : IntegrationTest({
        Given("유예 기준보다 오래된 탈퇴 사용자가 있을 때") {
            val ports =
                RecordingDeletionPorts(
                    board = boardDeletionPort,
                    sticker = stickerDeletionPort,
                    analysis = analysisDeletionPort,
                    termAgreement = termAgreementDeletionPort,
                )
            val cleanupService =
                WithdrawnUserCleanupService(
                    userRepository = userRepository,
                    boardDeletionPort = ports.boardPort,
                    stickerDeletionPort = ports.stickerPort,
                    analysisDeletionPort = ports.analysisPort,
                    termAgreementDeletionPort = ports.termAgreementPort,
                )
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
                WithdrawnUserCleanupService(
                    userRepository = userRepository,
                    boardDeletionPort = boardDeletionPort,
                    stickerDeletionPort = WithdrawnUserStickerDeletionPort { error("연관 데이터 삭제 실패") },
                    analysisDeletionPort = analysisDeletionPort,
                    termAgreementDeletionPort = termAgreementDeletionPort,
                )

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

        Given("배치 크기가 허용 범위를 벗어난 요청이 있을 때") {
            val cleanupService =
                WithdrawnUserCleanupService(
                    userRepository = userRepository,
                    boardDeletionPort = boardDeletionPort,
                    stickerDeletionPort = stickerDeletionPort,
                    analysisDeletionPort = analysisDeletionPort,
                    termAgreementDeletionPort = termAgreementDeletionPort,
                )

            When("상한을 넘긴 배치 크기로 정리 배치를 실행하면") {
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        cleanupService.cleanup(
                            deletedBefore = Instant.parse("2026-07-04T00:00:00Z"),
                            batchSize = 1001,
                        )
                    }

                Then("1 이상 1000 이하 범위를 알리며 거부한다") {
                    exception.message shouldBe BATCH_SIZE_RANGE_MESSAGE
                }
            }

            When("하한 미만인 배치 크기로 정리 배치를 실행하면") {
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        cleanupService.cleanup(
                            deletedBefore = Instant.parse("2026-07-04T00:00:00Z"),
                            batchSize = 0,
                        )
                    }

                Then("같은 범위 메시지로 거부한다") {
                    exception.message shouldBe BATCH_SIZE_RANGE_MESSAGE
                }
            }
        }
    })
