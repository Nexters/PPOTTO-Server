package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.model.BoardEvent
import com.github.nexters.ppotto.board.application.port.BoardEventConnection
import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.support.addBoardMember
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext

class BoardEventSubscriptionServiceTest(
    boardEventSubscriptionService: BoardEventSubscriptionService,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
    dslContext: DSLContext,
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
    override fun send(event: BoardEvent): Boolean = true

    override fun heartbeat(): Boolean = true

    override fun complete() = Unit
}
