package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.analysis.infrastructure.persistence.AnalysisRepository
import com.github.nexters.ppotto.board.application.model.BoardEvent
import com.github.nexters.ppotto.board.application.model.BoardEventType
import com.github.nexters.ppotto.board.application.port.BoardEventConnection
import com.github.nexters.ppotto.board.domain.Board
import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.domain.BoardRole
import com.github.nexters.ppotto.board.domain.BoardShareScope
import com.github.nexters.ppotto.board.infrastructure.BoardInvitationRepository
import com.github.nexters.ppotto.board.infrastructure.BoardMembershipRepository
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.error.ConflictException
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.global.error.UnauthorizedException
import com.github.nexters.ppotto.sticker.domain.RecapCommentCreation
import com.github.nexters.ppotto.sticker.infrastructure.StickerRecapRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerViewRepository
import com.github.nexters.ppotto.sticker.support.textStickerCreation
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class BoardParticipationServiceTest(
    participationService: BoardParticipationService,
    invitationService: BoardInvitationService,
    shareService: BoardShareService,
    shareQueryService: BoardShareQueryService,
    invitationRepository: BoardInvitationRepository,
    membershipRepository: BoardMembershipRepository,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
    analysisRepository: AnalysisRepository,
    stickerRepository: StickerRepository,
    stickerRecapRepository: StickerRecapRepository,
    stickerViewRepository: StickerViewRepository,
    boardEventSubscriptionService: BoardEventSubscriptionService,
    transactionTemplate: TransactionTemplate,
) : IntegrationTest({
        Given("보드의 참여자 변경을 구독 중일 때") {
            When("새 멤버가 참여한 뒤 기존 멤버가 나가면") {
                Then("실제 참여 관계 변경만 남은 참여자에게 알린다") {
                    val owner = userRepository.saveTestUser()
                    val member = userRepository.saveTestUser()
                    val newcomer = userRepository.saveTestUser()
                    val board = boardRepository.save(owner.id)
                    membershipRepository.saveIfAbsent(board.id, member.id)
                    val invitation = invitationService.issue(board.id, owner.id)
                    val ownerConnection = ParticipationEventConnection()
                    val memberConnection = ParticipationEventConnection()
                    val ownerSubscription =
                        boardEventSubscriptionService.subscribe(board.id, owner.id, ownerConnection)
                    val memberSubscription =
                        boardEventSubscriptionService.subscribe(board.id, member.id, memberConnection)
                    try {
                        participationService.join(invitation.code, newcomer.id)
                        val ownerJoinEvent = ownerConnection.events.poll(3, TimeUnit.SECONDS)
                        val memberJoinEvent = memberConnection.events.poll(3, TimeUnit.SECONDS)
                        ownerJoinEvent?.type shouldBe BoardEventType.BOARD_PARTICIPANTS_UPDATED
                        memberJoinEvent?.type shouldBe BoardEventType.BOARD_PARTICIPANTS_UPDATED

                        participationService.join(invitation.code, newcomer.id)
                        ownerConnection.events.poll(200, TimeUnit.MILLISECONDS) shouldBe null

                        participationService.leave(board.id, member.id)
                        val ownerLeaveEvent = ownerConnection.events.poll(3, TimeUnit.SECONDS)
                        ownerLeaveEvent?.type shouldBe BoardEventType.BOARD_PARTICIPANTS_UPDATED
                        memberConnection.events.poll(200, TimeUnit.MILLISECONDS) shouldBe null
                        memberConnection.completed shouldBe true
                        shouldThrow<NotFoundException> {
                            boardEventSubscriptionService.subscribe(board.id, member.id, ParticipationEventConnection())
                        }
                    } finally {
                        ownerSubscription.close()
                        memberSubscription.close()
                    }
                }
            }
        }

        Given("신규 참여 트랜잭션이 롤백되면") {
            When("참여를 요청하면") {
                Then("참여 관계와 이벤트가 남지 않는다") {
                    val owner = userRepository.saveTestUser()
                    val newcomer = userRepository.saveTestUser()
                    val board = boardRepository.save(owner.id)
                    val invitation = invitationService.issue(board.id, owner.id)
                    val connection = ParticipationEventConnection()
                    val subscription = boardEventSubscriptionService.subscribe(board.id, owner.id, connection)
                    try {
                        transactionTemplate.executeWithoutResult { status ->
                            participationService.join(invitation.code, newcomer.id)
                            connection.events.isEmpty() shouldBe true
                            status.setRollbackOnly()
                        }

                        membershipRepository.exists(board.id, newcomer.id) shouldBe false
                        connection.events.poll(200, TimeUnit.MILLISECONDS) shouldBe null
                    } finally {
                        subscription.close()
                    }
                }
            }
        }

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

        Given("참여자가 있는 공동 보드에서") {
            val owner = userRepository.saveTestUser("김용희")
            val member = userRepository.saveTestUser("이수민")
            val stranger = userRepository.saveTestUser("박지훈")
            val board = boardRepository.save(owner.id, "제주 여행")
            membershipRepository.saveIfAbsent(board.id, member.id)

            When("참여자가 나가면") {
                val memberInvitation = invitationService.issue(board.id, member.id)
                val memberShare = shareService.share(board.id, member.id, BoardShareScope.RECAP)
                participationService.leave(board.id, member.id)

                Then("참여 관계가 사라지고 보드 목록에서 빠진다") {
                    membershipRepository.exists(board.id, member.id) shouldBe false
                    membershipRepository.findAccessibleByUserId(member.id).none { it.board.id == board.id } shouldBe true
                }

                Then("마지막 참여자가 나가면 방장 목록에서 개인 보드로 돌아간다") {
                    membershipRepository
                        .findAccessibleByUserId(owner.id)
                        .single { it.board.id == board.id }
                        .memberCount shouldBe 0
                }

                Then("나간 참여자의 공유 링크는 더 이상 열리지 않는다") {
                    shouldThrow<NotFoundException> { shareQueryService.getSharedBoard(memberShare.shareToken) }
                        .errorCode shouldBe BoardErrorCode.SHARE_NOT_FOUND
                }

                Then("나간 참여자가 발급한 초대 코드는 만료 전까지 유효하다") {
                    participationService.join(memberInvitation.code, stranger.id).role shouldBe BoardRole.MEMBER
                }

                Then("다시 나가기를 요청하면 보드를 찾을 수 없다") {
                    shouldThrow<NotFoundException> { participationService.leave(board.id, member.id) }
                        .errorCode shouldBe BoardErrorCode.NOT_FOUND
                }

                Then("초대 코드로 다시 참여할 수 있다") {
                    val ownerInvitation = invitationService.issue(board.id, owner.id)
                    participationService.join(ownerInvitation.code, member.id).role shouldBe BoardRole.MEMBER
                }
            }

            When("방장이 나가려 하면") {
                Then("방장은 나갈 수 없어 거부하고 참여 관계는 그대로다") {
                    shouldThrow<ConflictException> { participationService.leave(board.id, owner.id) }
                        .errorCode shouldBe BoardErrorCode.OWNER_CANNOT_LEAVE
                    membershipRepository.exists(board.id, member.id) shouldBe true
                }
            }

            When("참여하지 않은 사용자가 나가려 하면") {
                Then("보드를 찾을 수 없다") {
                    shouldThrow<NotFoundException> { participationService.leave(board.id, stranger.id) }
                        .errorCode shouldBe BoardErrorCode.NOT_FOUND
                }
            }

            When("보드가 삭제된 뒤 참여자가 나가려 하면") {
                boardRepository.softDelete(board.id, owner.id)

                Then("보드를 찾을 수 없다") {
                    shouldThrow<NotFoundException> { participationService.leave(board.id, member.id) }
                        .errorCode shouldBe BoardErrorCode.NOT_FOUND
                }
            }
        }

        Given("오너와 멤버가 각각 스티커와 리캡을 만든 공동 보드에서") {
            val owner = userRepository.saveTestUser()
            val member = userRepository.saveTestUser()
            val board = boardRepository.save(owner.id)
            membershipRepository.saveIfAbsent(board.id, member.id)
            val ownerAnalysis = analysisRepository.save(owner.id, board.id)
            val memberAnalysis = analysisRepository.save(member.id, board.id)
            val ownerSticker =
                stickerRepository.save(ownerAnalysis.id, board.id, textStickerCreation(title = "오너 스티커"))
            val memberSticker =
                stickerRepository.save(memberAnalysis.id, board.id, textStickerCreation(title = "멤버 스티커"))
            stickerRecapRepository.saveComments(
                ownerSticker.id,
                listOf(RecapCommentCreation("오너 리캡", null, null)),
            )
            stickerRecapRepository.saveComments(
                memberSticker.id,
                listOf(RecapCommentCreation("멤버 리캡", null, null)),
            )
            stickerViewRepository.saveIfAbsent(ownerSticker.id, member.id)
            stickerViewRepository.saveIfAbsent(ownerSticker.id, owner.id)

            When("멤버가 보드에서 나가면") {
                participationService.leave(board.id, member.id)

                Then("멤버가 생성한 스티커와 리캡만 삭제한다") {
                    stickerRepository.findById(memberSticker.id).shouldBeNull()
                    stickerRecapRepository.findComments(memberSticker.id).shouldBeEmpty()
                    stickerRepository.findById(ownerSticker.id).shouldNotBeNull()
                    stickerRecapRepository
                        .findComments(ownerSticker.id)
                        .single()
                        .content shouldBe "오너 리캡"
                }

                Then("나간 멤버의 열람 기록만 삭제한다") {
                    stickerViewRepository.findViewedStickerIds(member.id, listOf(ownerSticker.id)) shouldBe emptySet()
                    stickerViewRepository.findViewedStickerIds(owner.id, listOf(ownerSticker.id)) shouldBe setOf(ownerSticker.id)
                }

                Then("다시 참여하면 이전 열람 기록이 복구되지 않는다") {
                    membershipRepository.saveIfAbsent(board.id, member.id)
                    stickerViewRepository.findViewedStickerIds(member.id, listOf(ownerSticker.id)) shouldBe emptySet()
                }
            }
        }
    })

private class ParticipationEventConnection : BoardEventConnection {
    val events = LinkedBlockingQueue<BoardEvent>()

    @Volatile
    var completed = false

    override fun send(event: BoardEvent): Boolean = events.offer(event)

    override fun heartbeat(): Boolean = true

    override fun complete() {
        completed = true
    }
}
