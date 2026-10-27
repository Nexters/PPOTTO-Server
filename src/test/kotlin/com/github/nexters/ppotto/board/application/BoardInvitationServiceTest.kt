package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.infrastructure.BoardInvitationRepository
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.support.addBoardMember
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.runConcurrently
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import org.jooq.DSLContext
import java.time.Duration
import java.time.Instant

class BoardInvitationServiceTest(
    boardInvitationService: BoardInvitationService,
    boardInvitationRepository: BoardInvitationRepository,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
    dslContext: DSLContext,
) : IntegrationTest({
        Given("초대를 발급할 수 있는 보드 방장이") {
            val owner = userRepository.saveTestUser()
            val board = boardRepository.save(owner.id, "제주 여행")

            When("처음 초대를 발급하면") {
                val before = Instant.now()
                val result = boardInvitationService.issue(board.id, owner.id)
                val after = Instant.now()

                Then("6자리 코드와 30일 만료 시각을 반환한다") {
                    result.code shouldMatch Regex("^[A-HJ-NP-Z2-9]{6}$")
                    (result.expiresAt >= before.plus(Duration.ofDays(30))) shouldBe true
                    (result.expiresAt <= after.plus(Duration.ofDays(30))) shouldBe true
                    result.previewRequired shouldBe true
                }
            }

            When("유효한 초대를 다시 발급하면") {
                val first = boardInvitationService.issue(board.id, owner.id)
                val second = boardInvitationService.issue(board.id, owner.id)

                Then("기존 초대와 만료 시각을 그대로 재사용한다") {
                    second shouldBe first
                }
            }

            When("만료된 초대를 다시 발급하면") {
                val expired =
                    boardInvitationRepository.save(
                        boardId = board.id,
                        inviterUserId = owner.id,
                        code = "ABC234",
                        expiresAt = Instant.now().minusSeconds(1),
                    )
                val result = boardInvitationService.issue(board.id, owner.id)

                Then("같은 초대 행의 코드와 만료 시각을 교체한다") {
                    result.invitationId shouldBe expired.id
                    result.code shouldMatch Regex("^[A-HJ-NP-Z2-9]{6}$")
                    result.code shouldNotBe "ABC234"
                    boardInvitationRepository.findById(expired.id)?.code shouldBe result.code
                }
            }

            When("같은 사용자가 동시에 초대를 발급하면") {
                val results = runConcurrently(8) { boardInvitationService.issue(board.id, owner.id) }

                Then("모든 요청이 같은 초대를 반환한다") {
                    val invitations = results.map { it.getOrThrow() }
                    invitations.distinct() shouldHaveSize 1
                }
            }

            When("참여자가 자기 이름으로 초대를 발급하면") {
                val member = userRepository.saveTestUser()
                dslContext.addBoardMember(board.id, member.id)
                val result = boardInvitationService.issue(board.id, member.id)

                Then("참여자 소유의 초대가 저장된다") {
                    boardInvitationRepository.findById(result.invitationId)?.inviterUserId shouldBe member.id
                }
            }
        }
    })
