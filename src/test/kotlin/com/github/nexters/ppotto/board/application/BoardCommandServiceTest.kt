package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.domain.Board
import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.domain.DrawingScope
import com.github.nexters.ppotto.board.domain.NewDrawing
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.infrastructure.DrawingRepository
import com.github.nexters.ppotto.board.support.BoardTestConfig
import com.github.nexters.ppotto.board.support.FakeBoardAnalysisActivityPort
import com.github.nexters.ppotto.board.support.FakeBoardStickerPort
import com.github.nexters.ppotto.board.support.uuidV7
import com.github.nexters.ppotto.global.error.ConflictException
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.context.annotation.Import

@Import(BoardTestConfig::class)
class BoardCommandServiceTest(
    boardCommandService: BoardCommandService,
    boardRepository: BoardRepository,
    drawingRepository: DrawingRepository,
    userRepository: UserRepository,
    analysisActivityPort: FakeBoardAnalysisActivityPort,
    stickerPort: FakeBoardStickerPort,
) : IntegrationTest({
        Given("보드가 없는 사용자가") {
            val user = userRepository.save()

            When("기본 보드를 생성하면") {
                val board = boardCommandService.createDefault(user.id)

                Then("Board 1 이름으로 저장된다") {
                    board.name shouldBe "Board 1"
                    boardRepository.findOwnedById(board.id, user.id)?.name shouldBe "Board 1"
                }
            }
        }

        Given("활성 보드가 100개인 사용자가") {
            val user = userRepository.save()
            repeat(Board.MAX_COUNT) { boardRepository.save(user.id, Board.defaultName(it + 1)) }

            When("보드를 하나 더 생성하면") {
                Then("BOARD-003 오류가 발생한다") {
                    val exception =
                        shouldThrow<InvalidInputException> {
                            boardCommandService.create(user.id, null)
                        }
                    exception.errorCode shouldBe BoardErrorCode.COUNT_LIMIT_EXCEEDED
                }
            }
        }

        Given("보드가 하나만 남은 사용자가") {
            val user = userRepository.save()
            val board = boardRepository.save(user.id)

            When("마지막 보드를 삭제하면") {
                Then("BOARD-004 오류가 발생한다") {
                    val exception =
                        shouldThrow<ConflictException> {
                            boardCommandService.delete(board.id, user.id)
                        }
                    exception.errorCode shouldBe BoardErrorCode.LAST_BOARD_CANNOT_BE_DELETED
                }
            }
        }

        Given("분석이 진행 중인 보드가 있는 사용자가") {
            analysisActivityPort.reset()
            val user = userRepository.save()
            val board = boardRepository.save(user.id)
            boardRepository.save(user.id)
            analysisActivityPort.activeBoardIds += board.id

            When("해당 보드를 삭제하면") {
                Then("BOARD-005 오류가 발생하고 보드는 유지된다") {
                    val exception =
                        shouldThrow<ConflictException> {
                            boardCommandService.delete(board.id, user.id)
                        }
                    exception.errorCode shouldBe BoardErrorCode.ACTIVE_ANALYSIS_EXISTS
                    boardRepository.findOwnedById(board.id, user.id)?.id shouldBe board.id
                }
            }
        }

        Given("삭제 가능한 보드와 드로잉이 있는 사용자가") {
            analysisActivityPort.reset()
            stickerPort.reset()
            val user = userRepository.save()
            val board = boardRepository.save(user.id)
            boardRepository.save(user.id)
            drawingRepository.upsertAll(
                listOf(
                    NewDrawing(
                        id = uuidV7(),
                        boardId = board.id,
                        stickerId = null,
                        scope = DrawingScope.BOARD,
                        stroke = mapOf("points" to listOf(listOf(1.0, 2.0))),
                        color = "#FFFFFF",
                        strokeWidth = 2.0,
                    ),
                ),
            )

            When("보드를 삭제하면") {
                boardCommandService.delete(board.id, user.id)

                Then("보드와 드로잉을 숨기고 스티커 삭제를 위임한다") {
                    boardRepository.findOwnedById(board.id, user.id).shouldBeNull()
                    drawingRepository.findByBoardId(board.id).shouldBeEmpty()
                    stickerPort.deletedBoardIds shouldBe listOf(board.id)
                }
            }
        }
    })
