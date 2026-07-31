package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.analysis.application.AnalysisService
import com.github.nexters.ppotto.analysis.application.PhotoUploadItemRequest
import com.github.nexters.ppotto.analysis.support.AnalysisTestConfig
import com.github.nexters.ppotto.board.application.port.BoardStickerCommandPort
import com.github.nexters.ppotto.board.application.port.BoardStickerLayoutCommand
import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.jooq.tables.references.ANALYSIS
import com.github.nexters.ppotto.support.IntegrationTest
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
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Import(AnalysisTestConfig::class, BoardAnalysisDeletionConcurrencyTestConfiguration::class)
class BoardAnalysisDeletionConcurrencyTest(
    analysisService: AnalysisService,
    boardCommandService: BoardCommandService,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
    stickerPort: BlockingBoardStickerCommandPort,
    dslContext: DSLContext,
) : IntegrationTest({
        Given("보드 삭제가 진행 중 분석 확인을 통과하고 스티커 정리 단계에 머문 상태에서") {
            val user = userRepository.save()
            val board = boardRepository.save(user.id)
            boardRepository.save(user.id)
            val photos =
                (0 until 90).map { PhotoUploadItemRequest(Instant.now().plusSeconds(it.toLong()), "image/jpeg") }

            When("같은 보드를 대상으로 분석 생성을 동시에 요청하면") {
                val executor = Executors.newFixedThreadPool(2)
                val deleteFuture =
                    executor.submit(
                        Callable { runCatching { boardCommandService.delete(board.id, user.id) } },
                    )
                check(stickerPort.awaitDeleteInvocation())
                val createFuture =
                    executor.submit(
                        Callable { runCatching { analysisService.createAnalysis(user.id, board.id, photos) } },
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
                        boardRepository.findOwnedById(board.id, user.id).shouldBeNull()
                        dslContext.fetchCount(ANALYSIS, ANALYSIS.BOARD_ID.eq(board.id)) shouldBe 0
                    }
                }
            }
        }
    })

@TestConfiguration
class BoardAnalysisDeletionConcurrencyTestConfiguration {
    @Bean
    @Primary
    fun blockingBoardStickerCommandPort(): BlockingBoardStickerCommandPort = BlockingBoardStickerCommandPort()
}

class BlockingBoardStickerCommandPort : BoardStickerCommandPort {
    private val deleteInvocation = CountDownLatch(1)
    private val deleteRelease = CountDownLatch(1)

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
}
