package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.domain.Board
import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.domain.BoardRole
import com.github.nexters.ppotto.board.infrastructure.BoardInvitationRepository
import com.github.nexters.ppotto.board.infrastructure.BoardMembershipRepository
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.error.ConflictException
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.global.error.UnauthorizedException
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.time.Instant

class BoardParticipationServiceTest(
    participationService: BoardParticipationService,
    invitationService: BoardInvitationService,
    invitationRepository: BoardInvitationRepository,
    membershipRepository: BoardMembershipRepository,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        Given("유효한 보드 초대가") {
            val owner = userRepository.saveTestUser("김용희")
            val member = userRepository.saveTestUser("이수민")
            val newcomer = userRepository.saveTestUser("박지훈")
            val board = boardRepository.save(owner.id, "제주 여행")
            membershipRepository.saveIfAbsent(board.id, member.id)
            val invitation = invitationService.issue(board.id, owner.id)

            When("신규 사용자가 참여하면") {
                val result = participationService.join(invitation.code.lowercase(), newcomer.id)

                Then("멤버를 추가하고 MEMBER 결과를 반환한다") {
                    result.boardId shouldBe board.id
                    result.boardName shouldBe "제주 여행"
                    result.role shouldBe BoardRole.MEMBER
                    membershipRepository.exists(board.id, newcomer.id) shouldBe true
                    membershipRepository.countMembersByBoardId(board.id) shouldBe 2
                }
            }

            When("기존 멤버가 다시 참여하면") {
                val result = participationService.join(invitation.code, member.id)

                Then("인원을 늘리지 않고 MEMBER 결과를 반환한다") {
                    result.role shouldBe BoardRole.MEMBER
                    membershipRepository.countMembersByBoardId(board.id) shouldBe 1
                }
            }

            When("방장이 참여하면") {
                val result = participationService.join(invitation.code, owner.id)

                Then("멤버 행을 만들지 않고 OWNER 결과를 반환한다") {
                    result.role shouldBe BoardRole.OWNER
                    membershipRepository.exists(board.id, owner.id) shouldBe false
                    membershipRepository.countMembersByBoardId(board.id) shouldBe 1
                }
            }

            When("보드가 삭제된 뒤 참여하면") {
                boardRepository.softDelete(board.id, owner.id)

                Then("초대를 찾을 수 없다") {
                    shouldThrow<NotFoundException> { participationService.join(invitation.code, newcomer.id) }
                        .errorCode shouldBe BoardErrorCode.INVITATION_NOT_FOUND
                }
            }

            When("초대 발급자가 탈퇴한 뒤 참여하면") {
                userRepository.withdraw(owner.withdraw(Instant.now()))

                Then("초대를 찾을 수 없다") {
                    shouldThrow<NotFoundException> { participationService.join(invitation.code, newcomer.id) }
                        .errorCode shouldBe BoardErrorCode.INVITATION_NOT_FOUND
                }
            }

            When("탈퇴한 사용자가 참여하면") {
                userRepository.withdraw(newcomer.withdraw(Instant.now()))

                Then("인증 오류로 거부한다") {
                    shouldThrow<UnauthorizedException> { participationService.join(invitation.code, newcomer.id) }
                }
            }
        }

        Given("방장과 멤버 7명으로 가득 찬 보드의 초대가") {
            val owner = userRepository.saveTestUser()
            val newcomer = userRepository.saveTestUser()
            val board = boardRepository.save(owner.id)
            repeat(Board.MAX_PARTICIPANT_COUNT - 1) {
                membershipRepository.saveIfAbsent(board.id, userRepository.saveTestUser().id)
            }
            val invitation = invitationService.issue(board.id, owner.id)

            When("신규 사용자가 참여하면") {
                Then("정원 초과로 거부한다") {
                    shouldThrow<ConflictException> { participationService.join(invitation.code, newcomer.id) }
                        .errorCode shouldBe BoardErrorCode.PARTICIPANT_LIMIT_EXCEEDED
                }
            }
        }

        Given("만료된 초대가") {
            val owner = userRepository.saveTestUser()
            val newcomer = userRepository.saveTestUser()
            val board = boardRepository.save(owner.id)
            val invitation =
                invitationRepository.save(
                    boardId = board.id,
                    inviterUserId = owner.id,
                    code = "ABC234",
                    expiresAt = Instant.now().minusSeconds(1),
                )

            When("참여에 사용되면") {
                Then("초대를 찾을 수 없다") {
                    shouldThrow<NotFoundException> { participationService.join(invitation.code, newcomer.id) }
                        .errorCode shouldBe BoardErrorCode.INVITATION_NOT_FOUND
                }
            }
        }
    })
