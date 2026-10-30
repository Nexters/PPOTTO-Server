package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.domain.Board
import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.infrastructure.BoardMembershipRepository
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.error.ConflictException
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.runConcurrently
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class BoardParticipationConcurrencyTest(
    participationService: BoardParticipationService,
    invitationService: BoardInvitationService,
    membershipRepository: BoardMembershipRepository,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        Given("마지막 한 자리만 남은 보드의 초대가") {
            val owner = userRepository.saveTestUser()
            val board = boardRepository.save(owner.id)
            repeat(Board.MAX_PARTICIPANT_COUNT - 2) {
                membershipRepository.saveIfAbsent(board.id, userRepository.saveTestUser().id)
            }
            val newcomers = List(2) { userRepository.saveTestUser() }
            val invitation = invitationService.issue(board.id, owner.id)

            When("두 사용자가 동시에 참여하면") {
                val results =
                    runConcurrently(newcomers.size) { index ->
                        participationService.join(invitation.code, newcomers[index].id)
                    }

                Then("한 명만 참여하고 전체 인원은 8명을 유지한다") {
                    val failures = results.mapNotNull { it.exceptionOrNull() }
                    assertSoftly {
                        results.count { it.isSuccess } shouldBe 1
                        failures shouldHaveSize 1
                        failures
                            .single()
                            .shouldBeInstanceOf<ConflictException>()
                            .errorCode shouldBe
                            BoardErrorCode.PARTICIPANT_LIMIT_EXCEEDED
                        membershipRepository.countMembersByBoardId(board.id) shouldBe Board.MAX_PARTICIPANT_COUNT - 1
                    }
                }
            }
        }
    })
