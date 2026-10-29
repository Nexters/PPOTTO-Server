package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.model.BoardInvitationPreviewImage
import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.infrastructure.BoardInvitationRepository
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.support.FakeBoardInvitationPreviewStorage
import com.github.nexters.ppotto.board.support.addBoardMember
import com.github.nexters.ppotto.board.support.validWebp
import com.github.nexters.ppotto.global.error.ConflictException
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.jooq.tables.references.BOARD_INVITATIONS
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.runConcurrently
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
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
    previewStorage: FakeBoardInvitationPreviewStorage,
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

            When("현재 코드의 미리보기를 처음 업로드하면") {
                val invitation = boardInvitationService.issue(board.id, owner.id)
                val bytes = validWebp()
                val result =
                    boardInvitationService.uploadPreview(
                        invitation.invitationId,
                        owner.id,
                        invitation.code,
                        BoardInvitationPreviewImage.from(bytes),
                    )

                Then("고유한 키로 업로드하고 DB에 반영한 뒤 읽기 URL을 반환한다") {
                    val stored = boardInvitationRepository.findById(invitation.invitationId)!!
                    previewStorage.uploaded.getValue(stored.previewImageKey!!) shouldBe bytes
                    result.previewImageUrl shouldBe "https://preview.test/${stored.previewImageKey}"
                }
            }

            When("같은 코드의 미리보기를 교체하면") {
                val invitation = boardInvitationService.issue(board.id, owner.id)
                val image = BoardInvitationPreviewImage.from(validWebp())
                boardInvitationService.uploadPreview(invitation.invitationId, owner.id, invitation.code, image)
                val firstKey = boardInvitationRepository.findById(invitation.invitationId)!!.previewImageKey!!
                boardInvitationService.uploadPreview(invitation.invitationId, owner.id, invitation.code, image)

                Then("새 키를 반영하고 이전 이미지를 정리한다") {
                    val secondKey = boardInvitationRepository.findById(invitation.invitationId)!!.previewImageKey!!
                    secondKey shouldNotBe firstKey
                    previewStorage.deleted shouldBe listOf(firstKey)
                }
            }

            When("이미 갱신된 코드로 미리보기를 업로드하면") {
                val invitation = boardInvitationService.issue(board.id, owner.id)

                Then("BOARD-008로 거부하고 파일을 업로드하지 않는다") {
                    val exception =
                        shouldThrow<ConflictException> {
                            boardInvitationService.uploadPreview(
                                invitation.invitationId,
                                owner.id,
                                "ZZZZZZ",
                                BoardInvitationPreviewImage.from(validWebp()),
                            )
                        }
                    exception.errorCode shouldBe BoardErrorCode.INVITATION_CODE_RENEWED
                    previewStorage.uploaded.size shouldBe 0
                }
            }

            When("스토리지 업로드 도중 초대 코드가 갱신되면") {
                val invitation = boardInvitationService.issue(board.id, owner.id)
                previewStorage.afterUpload = {
                    dslContext
                        .update(BOARD_INVITATIONS)
                        .set(BOARD_INVITATIONS.CODE, "ZZZZZZ")
                        .where(BOARD_INVITATIONS.ID.eq(invitation.invitationId))
                        .execute()
                }

                Then("DB 반영을 거부하고 방금 업로드한 파일을 정리한다") {
                    val exception =
                        shouldThrow<ConflictException> {
                            boardInvitationService.uploadPreview(
                                invitation.invitationId,
                                owner.id,
                                invitation.code,
                                BoardInvitationPreviewImage.from(validWebp()),
                            )
                        }
                    exception.errorCode shouldBe BoardErrorCode.INVITATION_CODE_RENEWED
                    boardInvitationRepository.findById(invitation.invitationId)?.previewImageKey shouldBe null
                    previewStorage.uploaded shouldBe emptyMap()
                    previewStorage.deleted shouldHaveSize 1
                }
            }

            When("다른 사용자가 미리보기를 업로드하면") {
                val invitation = boardInvitationService.issue(board.id, owner.id)
                val stranger = userRepository.saveTestUser()

                Then("BOARD-006으로 초대를 숨긴다") {
                    val exception =
                        shouldThrow<NotFoundException> {
                            boardInvitationService.uploadPreview(
                                invitation.invitationId,
                                stranger.id,
                                invitation.code,
                                BoardInvitationPreviewImage.from(validWebp()),
                            )
                        }
                    exception.errorCode shouldBe BoardErrorCode.INVITATION_NOT_FOUND
                }
            }

            When("스토리지 업로드가 실패하면") {
                val invitation = boardInvitationService.issue(board.id, owner.id)
                previewStorage.uploadFailure = IllegalStateException("upload failed")

                Then("DB 미리보기 키를 변경하지 않는다") {
                    shouldThrow<IllegalStateException> {
                        boardInvitationService.uploadPreview(
                            invitation.invitationId,
                            owner.id,
                            invitation.code,
                            BoardInvitationPreviewImage.from(validWebp()),
                        )
                    }
                    boardInvitationRepository.findById(invitation.invitationId)?.previewImageKey shouldBe null
                }
            }

            When("미리보기가 있는 초대를 만료 후 재발급하면") {
                val invitation = boardInvitationService.issue(board.id, owner.id)
                val image = BoardInvitationPreviewImage.from(validWebp())
                boardInvitationService.uploadPreview(invitation.invitationId, owner.id, invitation.code, image)
                val previousKey = boardInvitationRepository.findById(invitation.invitationId)!!.previewImageKey!!
                dslContext
                    .update(BOARD_INVITATIONS)
                    .set(BOARD_INVITATIONS.EXPIRES_AT, Instant.now().minusSeconds(1))
                    .where(BOARD_INVITATIONS.ID.eq(invitation.invitationId))
                    .execute()
                val reissued = boardInvitationService.issue(board.id, owner.id)

                Then("이전 이미지를 정리하고 새 미리보기를 요구한다") {
                    previewStorage.deleted shouldBe listOf(previousKey)
                    reissued.previewRequired shouldBe true
                }
            }
        }
    })
