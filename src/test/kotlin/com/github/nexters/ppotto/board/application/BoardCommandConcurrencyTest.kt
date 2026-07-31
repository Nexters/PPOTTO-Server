package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.domain.Board
import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.support.BoardTestConfig
import com.github.nexters.ppotto.board.support.FakeBoardAnalysisActivityPort
import com.github.nexters.ppotto.board.support.FakeBoardStickerPort
import com.github.nexters.ppotto.global.error.ConflictException
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.springframework.context.annotation.Import
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Import(BoardTestConfig::class)
class BoardCommandConcurrencyTest(
    boardCommandService: BoardCommandService,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
    analysisActivityPort: FakeBoardAnalysisActivityPort,
    stickerPort: FakeBoardStickerPort,
) : IntegrationTest({
        Given("활성 보드가 99개인 사용자가") {
            val user = userRepository.save()
            repeat(Board.MAX_COUNT - 1) {
                boardRepository.save(user.id, Board.defaultName(it + 1))
            }

            When("기본 보드와 이름 있는 보드를 동시에 여러 개 생성하면") {
                val results =
                    runConcurrently(12) { index ->
                        if (index % 2 == 0) {
                            boardCommandService.createDefault(user.id)
                        } else {
                            boardCommandService.create(user.id, "동시 $index")
                        }
                    }

                Then("정확히 한 건만 생성되어 최대 개수를 넘지 않는다") {
                    val failures = results.mapNotNull { it.exceptionOrNull() }
                    assertSoftly {
                        results.count { it.isSuccess } shouldBe 1
                        failures shouldHaveSize 11
                        failures.forEach {
                            val exception = it.shouldBeInstanceOf<InvalidInputException>()
                            exception.errorCode shouldBe BoardErrorCode.COUNT_LIMIT_EXCEEDED
                        }
                        boardRepository.countByUserId(user.id) shouldBe Board.MAX_COUNT
                    }
                }
            }
        }

        Given("활성 보드가 두 개인 사용자가") {
            analysisActivityPort.reset()
            stickerPort.reset()
            val user = userRepository.save()
            val boards = List(2) { boardRepository.save(user.id) }

            When("두 보드를 동시에 삭제하면") {
                val results =
                    runConcurrently(boards.size) { index ->
                        boardCommandService.delete(boards[index].id, user.id)
                    }

                Then("한 보드는 남기고 다른 삭제 요청을 거부한다") {
                    val failures = results.mapNotNull { it.exceptionOrNull() }
                    assertSoftly {
                        results.count { it.isSuccess } shouldBe 1
                        failures shouldHaveSize 1
                        val exception = failures.single().shouldBeInstanceOf<ConflictException>()
                        exception.errorCode shouldBe BoardErrorCode.LAST_BOARD_CANNOT_BE_DELETED
                        boardRepository.findByUserId(user.id) shouldHaveSize 1
                    }
                }
            }
        }
    })

private fun <T> runConcurrently(
    taskCount: Int,
    task: (Int) -> T,
): List<Result<T>> {
    val ready = CountDownLatch(taskCount)
    val start = CountDownLatch(1)
    val executor = Executors.newFixedThreadPool(taskCount)
    return try {
        val futures =
            List(taskCount) { index ->
                executor.submit(
                    Callable {
                        ready.countDown()
                        start.await()
                        runCatching { task(index) }
                    },
                )
            }
        check(ready.await(10, TimeUnit.SECONDS))
        start.countDown()
        futures.map { it.get(30, TimeUnit.SECONDS) }
    } finally {
        executor.shutdownNow()
    }
}
