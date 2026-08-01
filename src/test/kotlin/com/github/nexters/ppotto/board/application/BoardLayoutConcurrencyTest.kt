package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.port.BoardAnalysisActivityPort
import com.github.nexters.ppotto.board.application.port.BoardStickerCommandPort
import com.github.nexters.ppotto.board.application.port.BoardStickerItem
import com.github.nexters.ppotto.board.application.port.BoardStickerLayoutCommand
import com.github.nexters.ppotto.board.application.port.BoardStickerQueryPort
import com.github.nexters.ppotto.board.domain.DrawingScope
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.infrastructure.DrawingRepository
import com.github.nexters.ppotto.board.support.uuidV7
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.DrawingId
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Import(BoardLayoutConcurrencyTestConfiguration::class)
class BoardLayoutConcurrencyTest(
    boardLayoutService: BoardLayoutService,
    boardCommandService: BoardCommandService,
    boardRepository: BoardRepository,
    drawingRepository: DrawingRepository,
    userRepository: UserRepository,
    stickerPort: CoordinatedBoardStickerPort,
) : IntegrationTest({
        Given("삭제 가능한 보드의 레이아웃 검증이 끝난 상태에서") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)
            boardRepository.save(user.id)
            val drawingId = DrawingId(uuidV7())
            val layoutCommand =
                BoardLayoutUpdateCommand(
                    stickers = emptyList(),
                    createdDrawings =
                        listOf(
                            DrawingCreateCommand(
                                id = drawingId,
                                scope = DrawingScope.BOARD,
                                stickerId = null,
                                stroke = mapOf("points" to listOf(listOf(1.0, 2.0))),
                                color = "#FFFFFF",
                                strokeWidth = 2.0,
                            ),
                        ),
                    deletedDrawingIds = emptyList(),
                )

            When("레이아웃 저장 중 같은 보드 삭제를 동시에 요청하면") {
                val executor = Executors.newFixedThreadPool(2)
                val deleteStarted = CountDownLatch(1)
                val layoutFuture =
                    executor.submit(
                        Callable {
                            runCatching {
                                boardLayoutService.update(board.id, user.id, layoutCommand)
                            }
                        },
                    )
                check(stickerPort.awaitLayoutValidation())
                val deleteFuture =
                    executor.submit(
                        Callable {
                            deleteStarted.countDown()
                            runCatching {
                                boardCommandService.delete(board.id, user.id)
                            }
                        },
                    )
                check(deleteStarted.await(10, TimeUnit.SECONDS))
                val deleteReachedStickerPortBeforeRelease = stickerPort.awaitDeleteInvocation()
                stickerPort.releaseLayout()
                val layoutResult = layoutFuture.get(30, TimeUnit.SECONDS)
                val deleteResult = deleteFuture.get(30, TimeUnit.SECONDS)
                executor.shutdownNow()

                Then("삭제가 레이아웃 트랜잭션 뒤에 실행되어 드로잉을 남기지 않는다") {
                    assertSoftly {
                        deleteReachedStickerPortBeforeRelease shouldBe false
                        layoutResult.isSuccess shouldBe true
                        deleteResult.isSuccess shouldBe true
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
    fun coordinatedBoardStickerPort(): CoordinatedBoardStickerPort = CoordinatedBoardStickerPort()
}

class CoordinatedBoardStickerPort :
    BoardStickerQueryPort,
    BoardStickerCommandPort {
    private val layoutValidation = CountDownLatch(1)
    private val layoutRelease = CountDownLatch(1)
    private val deleteInvocation = CountDownLatch(1)

    override fun getByBoardId(boardId: BoardId): List<BoardStickerItem> = emptyList()

    override fun validateOwnedByBoard(
        boardId: BoardId,
        stickerIds: Set<StickerId>,
    ) {
        layoutValidation.countDown()
        check(layoutRelease.await(10, TimeUnit.SECONDS))
    }

    override fun updateLayouts(
        boardId: BoardId,
        layouts: List<BoardStickerLayoutCommand>,
    ) = Unit

    override fun deleteAllByBoardId(boardId: BoardId) {
        deleteInvocation.countDown()
    }

    fun awaitLayoutValidation(): Boolean = layoutValidation.await(10, TimeUnit.SECONDS)

    fun awaitDeleteInvocation(): Boolean = deleteInvocation.await(500, TimeUnit.MILLISECONDS)

    fun releaseLayout() {
        layoutRelease.countDown()
    }
}
