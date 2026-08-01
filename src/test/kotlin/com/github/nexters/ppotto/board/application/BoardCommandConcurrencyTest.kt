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
import com.github.nexters.ppotto.support.rawId
import com.github.nexters.ppotto.support.runConcurrently
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.springframework.context.annotation.Import

@Import(BoardTestConfig::class)
class BoardCommandConcurrencyTest(
    boardCommandService: BoardCommandService,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
    analysisActivityPort: FakeBoardAnalysisActivityPort,
    stickerPort: FakeBoardStickerPort,
) : IntegrationTest({
        Given("활성 보드가 99개인 사용자가") {
            val user = userRepository.saveTestUser()
            repeat(Board.MAX_COUNT - 1) {
                boardRepository.save(user.rawId, Board.defaultName(it + 1))
            }

            When("기본 보드와 이름 있는 보드를 동시에 여러 개 생성하면") {
                val results =
                    runConcurrently(12) { index ->
                        if (index % 2 == 0) {
                            boardCommandService.createDefault(user.rawId)
                        } else {
                            boardCommandService.create(user.rawId, "동시 $index")
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
                        boardRepository.countByUserId(user.rawId) shouldBe Board.MAX_COUNT
                    }
                }
            }
        }

        Given("활성 보드가 두 개인 사용자가") {
            analysisActivityPort.reset()
            stickerPort.reset()
            val user = userRepository.saveTestUser()
            val boards = List(2) { boardRepository.save(user.rawId) }

            When("두 보드를 동시에 삭제하면") {
                val results =
                    runConcurrently(boards.size) { index ->
                        boardCommandService.delete(boards[index].id, user.rawId)
                    }

                Then("한 보드는 남기고 다른 삭제 요청을 거부한다") {
                    val failures = results.mapNotNull { it.exceptionOrNull() }
                    assertSoftly {
                        results.count { it.isSuccess } shouldBe 1
                        failures shouldHaveSize 1
                        val exception = failures.single().shouldBeInstanceOf<ConflictException>()
                        exception.errorCode shouldBe BoardErrorCode.LAST_BOARD_CANNOT_BE_DELETED
                        boardRepository.findByUserId(user.rawId) shouldHaveSize 1
                    }
                }
            }
        }
    })
