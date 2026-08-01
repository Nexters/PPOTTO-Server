package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.analysis.application.AnalysisService
import com.github.nexters.ppotto.analysis.application.PhotoUploadItemRequest
import com.github.nexters.ppotto.analysis.support.AnalysisTestConfig
import com.github.nexters.ppotto.board.application.port.BoardStickerCommandPort
import com.github.nexters.ppotto.board.application.port.BoardStickerLayoutCommand
import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.error.ConflictException
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.jooq.tables.references.ANALYSIS
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.rawId
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.jooq.DSLContext
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val PHOTO_COUNT = 90

@Import(AnalysisTestConfig::class, BoardAnalysisDeletionConcurrencyTestConfiguration::class)
class BoardAnalysisDeletionConcurrencyTest(
    analysisService: AnalysisService,
    boardCommandService: BoardCommandService,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
    stickerPort: BlockingBoardStickerCommandPort,
    transactionTemplate: TransactionTemplate,
    dslContext: DSLContext,
) : IntegrationTest({
        beforeTest { stickerPort.reset() }

        Given("보드 삭제가 진행 중 분석 확인을 통과하고 스티커 정리 단계에 머문 상태에서") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.rawId)
            boardRepository.save(user.rawId)
            val photos = photoRequests()

            When("같은 보드를 대상으로 분석 생성을 동시에 요청하면") {
                val executor = Executors.newFixedThreadPool(2)
                val deleteFuture =
                    executor.submit(
                        Callable { runCatching { boardCommandService.delete(board.id, user.rawId) } },
                    )
                check(stickerPort.awaitDeleteInvocation())
                val createFuture =
                    executor.submit(
                        Callable { runCatching { analysisService.createAnalysis(user.rawId, board.id, photos) } },
                    )
                val createBlockedBeforeRelease = runCatching { createFuture.get(1, TimeUnit.SECONDS) }.isFailure
                stickerPort.releaseDelete()
                val deleteResult = deleteFuture.get(30, TimeUnit.SECONDS)
                val createResult = createFuture.get(30, TimeUnit.SECONDS)
                executor.shutdownNow()

                Then("분석 생성이 삭제 뒤로 직렬화되어 삭제된 보드에 분석이 남지 않는다") {
                    assertSoftly {
                        createBlockedBeforeRelease shouldBe true
                        deleteResult.isSuccess shouldBe true
                        createResult
                            .exceptionOrNull()
                            .shouldBeInstanceOf<NotFoundException>()
                            .errorCode shouldBe BoardErrorCode.NOT_FOUND
                        boardRepository.findOwnedById(board.id, user.rawId).shouldBeNull()
                        dslContext.fetchCount(ANALYSIS, ANALYSIS.BOARD_ID.eq(board.id)) shouldBe 0
                    }
                }
            }
        }

        Given("분석 생성이 대상 보드 행을 잠근 채 커밋 전에 머문 상태에서") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.rawId)
            boardRepository.save(user.rawId)
            val photos = photoRequests()

            When("같은 보드를 대상으로 보드 삭제를 동시에 요청하면") {
                stickerPort.releaseDelete()
                val executor = Executors.newFixedThreadPool(2)
                val createLocked = CountDownLatch(1)
                val createCommitAllowed = CountDownLatch(1)
                val createFuture =
                    executor.submit(
                        Callable {
                            runCatching {
                                transactionTemplate.executeWithoutResult {
                                    analysisService.createAnalysis(user.rawId, board.id, photos)
                                    createLocked.countDown()
                                    check(createCommitAllowed.await(10, TimeUnit.SECONDS))
                                }
                            }
                        },
                    )
                check(createLocked.await(30, TimeUnit.SECONDS))
                val deleteFuture =
                    executor.submit(
                        Callable { runCatching { boardCommandService.delete(board.id, user.rawId) } },
                    )
                val deleteBlockedBeforeCommit = runCatching { deleteFuture.get(1, TimeUnit.SECONDS) }.isFailure
                createCommitAllowed.countDown()
                val createResult = createFuture.get(30, TimeUnit.SECONDS)
                val deleteResult = deleteFuture.get(30, TimeUnit.SECONDS)
                executor.shutdownNow()

                Then("삭제가 분석 생성 뒤로 직렬화되어 BOARD-005로 거부되고 보드와 분석이 남는다") {
                    assertSoftly {
                        deleteBlockedBeforeCommit shouldBe true
                        createResult.isSuccess shouldBe true
                        deleteResult
                            .exceptionOrNull()
                            .shouldBeInstanceOf<ConflictException>()
                            .errorCode shouldBe BoardErrorCode.ACTIVE_ANALYSIS_EXISTS
                        boardRepository.findOwnedById(board.id, user.rawId)?.id shouldBe board.id
                        dslContext.fetchCount(ANALYSIS, ANALYSIS.BOARD_ID.eq(board.id)) shouldBe 1
                    }
                }
            }
        }
    })

private fun photoRequests(): List<PhotoUploadItemRequest> =
    (0 until PHOTO_COUNT).map { PhotoUploadItemRequest(Instant.now().plusSeconds(it.toLong()), "image/jpeg") }

@TestConfiguration
class BoardAnalysisDeletionConcurrencyTestConfiguration {
    @Bean
    @Primary
    fun blockingBoardStickerCommandPort(): BlockingBoardStickerCommandPort = BlockingBoardStickerCommandPort()
}

class BlockingBoardStickerCommandPort : BoardStickerCommandPort {
    @Volatile
    private var deleteInvocation = CountDownLatch(1)

    @Volatile
    private var deleteRelease = CountDownLatch(1)

    override fun validateOwnedByBoard(
        boardId: UUID,
        stickerIds: Set<UUID>,
    ) = Unit

    override fun updateLayouts(
        boardId: UUID,
        layouts: List<BoardStickerLayoutCommand>,
    ) = Unit

    override fun deleteAllByBoardId(boardId: UUID) {
        deleteInvocation.countDown()
        check(deleteRelease.await(10, TimeUnit.SECONDS))
    }

    fun awaitDeleteInvocation(): Boolean = deleteInvocation.await(10, TimeUnit.SECONDS)

    fun releaseDelete() {
        deleteRelease.countDown()
    }

    fun reset() {
        deleteInvocation = CountDownLatch(1)
        deleteRelease = CountDownLatch(1)
    }
}
