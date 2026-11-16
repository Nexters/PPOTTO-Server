package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.model.BoardEvent
import com.github.nexters.ppotto.board.application.model.BoardEventType
import com.github.nexters.ppotto.board.application.port.BoardEventConnection
import com.github.nexters.ppotto.board.domain.Board
import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.domain.DrawingScope
import com.github.nexters.ppotto.board.domain.NewDrawing
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.infrastructure.DrawingRepository
import com.github.nexters.ppotto.board.support.BoardTestConfig
import com.github.nexters.ppotto.board.support.FakeBoardAnalysisActivityPort
import com.github.nexters.ppotto.board.support.FakeBoardStickerPort
import com.github.nexters.ppotto.board.support.addBoardMember
import com.github.nexters.ppotto.board.support.uuidV7
import com.github.nexters.ppotto.global.error.ConflictException
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.identifier.DrawingId
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import org.springframework.context.annotation.Import
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@Import(BoardTestConfig::class)
class BoardCommandServiceTest(
    boardCommandService: BoardCommandService,
    boardEventBroker: BoardEventBroker,
    boardRepository: BoardRepository,
    drawingRepository: DrawingRepository,
    userRepository: UserRepository,
    analysisActivityPort: FakeBoardAnalysisActivityPort,
    stickerPort: FakeBoardStickerPort,
    dsl: DSLContext,
) : IntegrationTest({
        Given("이벤트를 구독 중인 보드의 방장이") {
            val owner = userRepository.saveTestUser()
            val board = boardRepository.save(owner.id)

            When("보드 이름을 변경하면") {
                Then("변경된 이름을 저장하고 이름 변경 이벤트를 보낸다") {
                    val events = LinkedBlockingQueue<BoardEvent>()
                    val subscription =
                        boardEventBroker.subscribe(
                            board.id,
                            owner.id,
                            object : BoardEventConnection {
                                override fun send(event: BoardEvent): Boolean {
                                    events.add(event)
                                    return true
                                }

                                override fun heartbeat(): Boolean = true

                                override fun complete() = Unit
                            },
                        )
                    try {
                        boardCommandService.rename(board.id, owner.id, "바뀐 보드")

                        boardRepository.findOwnedById(board.id, owner.id)?.name shouldBe "바뀐 보드"
                        val event = events.poll(3, TimeUnit.SECONDS)
                        event?.type shouldBe BoardEventType.BOARD_UPDATED
                        event?.boardId shouldBe board.id
                    } finally {
                        subscription.close()
                    }
                }
            }
        }

        Given("보드가 없는 사용자가") {
            val user = userRepository.saveTestUser()

            When("기본 보드를 생성하면") {
                val board = boardCommandService.createDefault(user.id)

                Then("Board 1 이름으로 저장된다") {
                    board.name shouldBe "Board 1"
                    boardRepository.findOwnedById(board.id, user.id)?.name shouldBe "Board 1"
                }
            }
        }

        Given("활성 보드가 100개인 사용자가") {
            val user = userRepository.saveTestUser()
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
            val user = userRepository.saveTestUser()
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
            val user = userRepository.saveTestUser()
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
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)
            boardRepository.save(user.id)
            drawingRepository.upsertAll(
                listOf(
                    NewDrawing.Stroke(
                        id = DrawingId(uuidV7()),
                        boardId = board.id,
                        stickerId = null,
                        scope = DrawingScope.BOARD,
                        zIndex = 0,
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

        Given("공동 보드의 방장과 참여자가 이벤트를 구독 중일 때") {
            val owner = userRepository.saveTestUser()
            val member = userRepository.saveTestUser()
            val board = boardRepository.save(owner.id)
            boardRepository.save(owner.id)
            dsl.addBoardMember(board.id, member.id)

            When("방장이 보드를 삭제하면") {
                Then("두 사용자에게 삭제 이벤트를 보내고 연결을 종료한다") {
                    val ownerEvents = LinkedBlockingQueue<BoardEvent>()
                    val memberEvents = LinkedBlockingQueue<BoardEvent>()
                    val completed = CountDownLatch(2)
                    val ownerSubscription =
                        boardEventBroker.subscribe(board.id, owner.id, recordingConnection(ownerEvents, completed))
                    val memberSubscription =
                        boardEventBroker.subscribe(board.id, member.id, recordingConnection(memberEvents, completed))
                    try {
                        boardCommandService.delete(board.id, owner.id)

                        ownerEvents.poll(3, TimeUnit.SECONDS)?.type shouldBe BoardEventType.BOARD_DELETED
                        memberEvents.poll(3, TimeUnit.SECONDS)?.type shouldBe BoardEventType.BOARD_DELETED
                        completed.await(3, TimeUnit.SECONDS) shouldBe true
                    } finally {
                        ownerSubscription.close()
                        memberSubscription.close()
                    }
                }
            }
        }
    })

private fun recordingConnection(
    events: LinkedBlockingQueue<BoardEvent>,
    completed: CountDownLatch,
): BoardEventConnection =
    object : BoardEventConnection {
        override fun send(event: BoardEvent): Boolean {
            events.add(event)
            return true
        }

        override fun heartbeat(): Boolean = true

        override fun complete() {
            completed.countDown()
        }
    }
