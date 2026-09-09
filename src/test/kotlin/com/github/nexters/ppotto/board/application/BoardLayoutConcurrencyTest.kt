package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.port.BoardAnalysisActivityPort
import com.github.nexters.ppotto.board.application.port.BoardStickerCommandPort
import com.github.nexters.ppotto.board.application.port.BoardStickerItem
import com.github.nexters.ppotto.board.application.port.BoardStickerLayoutCommand
import com.github.nexters.ppotto.board.application.port.BoardStickerQueryPort
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.infrastructure.DrawingRepository
import com.github.nexters.ppotto.board.support.awaitBlockedLock
import com.github.nexters.ppotto.board.support.newDrawing
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.jooq.tables.references.DRAWINGS
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val AWAIT_SECONDS = 10L

@Import(BoardLayoutConcurrencyTestConfiguration::class)
class BoardLayoutConcurrencyTest(
    boardLayoutService: BoardLayoutService,
    boardCommandService: BoardCommandService,
    boardRepository: BoardRepository,
    drawingRepository: DrawingRepository,
    userRepository: UserRepository,
    stickerPort: CoordinatedBoardStickerPort,
    dslContext: DSLContext,
) : IntegrationTest({
        beforeTest { stickerPort.reset() }

        Given("삭제 가능한 보드의 레이아웃 검증이 끝난 상태에서") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)
            boardRepository.save(user.id)
            val drawing = newDrawing(boardId = board.id)
            val layoutCommand =
                BoardLayoutUpdateCommand(
                    stickers = emptyList(),
                    createdDrawings = listOf(drawing),
                    deletedDrawingIds = emptyList(),
                )

            When("레이아웃 저장 중 같은 보드 삭제를 동시에 요청하면") {
                val executor = Executors.newFixedThreadPool(2)
                val deleteStarted = CountDownLatch(1)
                val layoutFuture =
                    executor.submit(
                        Callable { runCatching { boardLayoutService.update(board.id, user.id, layoutCommand) } },
                    )
                check(stickerPort.awaitLayoutValidation())
                val deleteFuture =
                    executor.submit(
                        Callable {
                            deleteStarted.countDown()
                            runCatching { boardCommandService.delete(board.id, user.id) }
                        },
                    )
                check(deleteStarted.await(AWAIT_SECONDS, TimeUnit.SECONDS))
                dslContext.awaitBlockedLock()
                stickerPort.releaseLayout()
                val layoutResult = layoutFuture.get(30, TimeUnit.SECONDS)
                val deleteResult = deleteFuture.get(30, TimeUnit.SECONDS)
                val drawingRowsAtDelete = stickerPort.drawingRowsAtDelete
                executor.shutdownNow()

                Then("삭제가 레이아웃이 커밋한 드로잉을 관찰한 뒤 실행되어 드로잉을 남기지 않는다") {
                    assertSoftly {
                        layoutResult.isSuccess shouldBe true
                        deleteResult.isSuccess shouldBe true
                        drawingRowsAtDelete shouldBe 1
                        boardRepository.findOwnedById(board.id, user.id).shouldBeNull()
                        drawingRepository.findByBoardId(board.id).shouldBeEmpty()
                    }
                }
            }
        }
    })

@TestConfiguration
class BoardLayoutConcurrencyTestConfiguration {
    @Bean
    @Primary
    fun completedBoardAnalysisActivityPort(): BoardAnalysisActivityPort = BoardAnalysisActivityPort { _, _ -> false }

    @Bean
    @Primary
    fun coordinatedBoardStickerPort(dslContext: DSLContext): CoordinatedBoardStickerPort = CoordinatedBoardStickerPort(dslContext)
}

class CoordinatedBoardStickerPort(
    private val dslContext: DSLContext,
) : BoardStickerQueryPort,
    BoardStickerCommandPort {
    @Volatile
    private var layoutValidation = CountDownLatch(1)

    @Volatile
    private var layoutRelease = CountDownLatch(1)

    @Volatile
    var drawingRowsAtDelete: Int = -1
        private set

    override fun getByBoardId(boardId: BoardId): List<BoardStickerItem> = emptyList()

    override fun ownsAll(
        boardId: BoardId,
        stickerIds: Set<StickerId>,
    ): Boolean {
        layoutValidation.countDown()
        check(layoutRelease.await(AWAIT_SECONDS, TimeUnit.SECONDS))
        return true
    }

    override fun updateLayouts(
        boardId: BoardId,
        layouts: List<BoardStickerLayoutCommand>,
    ) = Unit

    override fun deleteAllByBoardId(boardId: BoardId) {
        drawingRowsAtDelete = dslContext.fetchCount(DRAWINGS, DRAWINGS.BOARD_ID.eq(boardId))
    }

    fun awaitLayoutValidation(): Boolean = layoutValidation.await(AWAIT_SECONDS, TimeUnit.SECONDS)

    fun releaseLayout() {
        layoutRelease.countDown()
    }

    fun reset() {
        layoutValidation = CountDownLatch(1)
        layoutRelease = CountDownLatch(1)
        drawingRowsAtDelete = -1
    }
}
