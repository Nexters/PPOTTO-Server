package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.model.BoardEvent
import com.github.nexters.ppotto.board.application.model.BoardLayoutUpdateCommand
import com.github.nexters.ppotto.board.application.port.BoardEventConnection
import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.support.BoardTestConfig
import com.github.nexters.ppotto.board.support.addBoardMember
import com.github.nexters.ppotto.board.support.newDrawing
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import org.springframework.context.annotation.Import
import org.springframework.transaction.support.TransactionTemplate

@Import(BoardTestConfig::class)
class BoardEventSubscriptionServiceTest(
    boardEventSubscriptionService: BoardEventSubscriptionService,
    boardLayoutService: BoardLayoutService,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
    dslContext: DSLContext,
    transactionTemplate: TransactionTemplate,
) : IntegrationTest({
        Given("방장과 참여자가 있는 공동 보드에서") {
            val owner = userRepository.saveTestUser()
            val member = userRepository.saveTestUser()
            val stranger = userRepository.saveTestUser()
            val board = boardRepository.save(owner.id)
            dslContext.addBoardMember(board.id, member.id)

            When("방장과 참여자가 이벤트를 구독하면") {
                val ownerSubscription =
                    boardEventSubscriptionService.subscribe(board.id, owner.id, RecordingConnection())
                val memberSubscription =
                    boardEventSubscriptionService.subscribe(board.id, member.id, RecordingConnection())

                Then("두 사용자의 구독을 허용한다") {
                    ownerSubscription.close()
                    memberSubscription.close()
                }
            }

            When("레이아웃 저장 트랜잭션이 커밋되면") {
                val ownerConnection = RecordingConnection()
                val memberConnection = RecordingConnection()
                val ownerSubscription = boardEventSubscriptionService.subscribe(board.id, owner.id, ownerConnection)
                val memberSubscription = boardEventSubscriptionService.subscribe(board.id, member.id, memberConnection)

                Then("같은 보드를 구독한 방장과 참여자에게 이벤트를 전달한다") {
                    try {
                        transactionTemplate.executeWithoutResult {
                            boardLayoutService.update(board.id, member.id, layoutCommand(board.id))
                            ownerConnection.events.shouldBeEmpty()
                            memberConnection.events.shouldBeEmpty()
                        }

                        ownerConnection.events
                            .single()
                            .boardId shouldBe board.id
                        memberConnection.events shouldBe ownerConnection.events
                    } finally {
                        ownerSubscription.close()
                        memberSubscription.close()
                    }
                }
            }

            When("레이아웃 저장 트랜잭션이 롤백되면") {
                val connection = RecordingConnection()
                val subscription = boardEventSubscriptionService.subscribe(board.id, owner.id, connection)

                Then("이벤트를 전달하지 않는다") {
                    try {
                        transactionTemplate.executeWithoutResult { status ->
                            boardLayoutService.update(board.id, member.id, layoutCommand(board.id))
                            status.setRollbackOnly()
                        }

                        connection.events.shouldBeEmpty()
                    } finally {
                        subscription.close()
                    }
                }
            }

            When("보드와 무관한 사용자가 구독하면") {
                Then("BOARD-002로 거부한다") {
                    shouldThrow<NotFoundException> {
                        boardEventSubscriptionService.subscribe(board.id, stranger.id, RecordingConnection())
                    }.errorCode shouldBe BoardErrorCode.NOT_FOUND
                }
            }
        }
    })

private class RecordingConnection : BoardEventConnection {
    val events = mutableListOf<BoardEvent>()

    override fun send(event: BoardEvent): Boolean {
        events += event
        return true
    }

    override fun heartbeat(): Boolean = true

    override fun complete() = Unit
}

private fun layoutCommand(boardId: BoardId) =
    BoardLayoutUpdateCommand(
        stickers = emptyList(),
        createdDrawings = listOf(newDrawing(boardId = boardId)),
        deletedDrawingIds = emptyList(),
    )
