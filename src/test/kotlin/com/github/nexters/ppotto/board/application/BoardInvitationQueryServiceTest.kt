package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.analysis.infrastructure.persistence.AnalysisRepository
import com.github.nexters.ppotto.board.application.model.BoardInvitationViewerStatus
import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.domain.BoardRole
import com.github.nexters.ppotto.board.infrastructure.BoardInvitationRepository
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.support.addBoardMember
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.sticker.infrastructure.StickerRepository
import com.github.nexters.ppotto.sticker.support.textStickerCreation
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import java.time.Instant

class BoardInvitationQueryServiceTest(
    queryService: BoardInvitationQueryService,
    invitationService: BoardInvitationService,
    invitationRepository: BoardInvitationRepository,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
    analysisRepository: AnalysisRepository,
    stickerRepository: StickerRepository,
    dslContext: DSLContext,
) : IntegrationTest({
        Given("방장과 참여자가 있는 보드의 유효한 초대가") {
            val owner = userRepository.saveTestUser("김용희")
            val member = userRepository.saveTestUser("이수민")
            val stranger = userRepository.saveTestUser("박영희")
            val board = boardRepository.save(owner.id, "제주 여행")
            dslContext.addBoardMember(board.id, member.id)
            val issued = invitationService.issue(board.id, owner.id)
            val previewImageKey = "board-invitations/${issued.invitationId}/preview.webp"
            invitationRepository.updatePreviewImageKey(issued.invitationId, previewImageKey)
            val analysis = analysisRepository.save(owner.id, board.id)
            repeat(2) { stickerRepository.save(analysis.id, board.id, textStickerCreation(title = "스티커 $it")) }

            When("익명으로 조회되면") {
                val detail = queryService.get(issued.code, null)

                Then("현재 공개 정보만 반환한다") {
                    detail.boardId shouldBe board.id
                    detail.boardName shouldBe "제주 여행"
                    detail.inviterName shouldBe "김용희"
                    detail.participantCount shouldBe 2
                    detail.maxParticipantCount shouldBe 8
                    detail.stickerCount shouldBe 2
                    detail.previewImageUrl shouldBe "https://preview.test/$previewImageKey"
                    detail.viewerStatus shouldBe null
                    detail.participants shouldBe null
                }
            }

            When("방장·참여자·미참여자가 조회하면") {
                val ownerDetail = queryService.get(issued.code, owner.id)
                val memberDetail = queryService.get(issued.code, member.id)
                val strangerDetail = queryService.get(issued.code.lowercase(), stranger.id)

                Then("각 사용자 상태와 방장 우선 참여자 목록을 반환한다") {
                    ownerDetail.viewerStatus shouldBe BoardInvitationViewerStatus.OWNER
                    memberDetail.viewerStatus shouldBe BoardInvitationViewerStatus.MEMBER
                    strangerDetail.viewerStatus shouldBe BoardInvitationViewerStatus.NOT_JOINED
                    strangerDetail.participants?.map { it.name to it.role } shouldBe
                        listOf(
                            "김용희" to BoardRole.OWNER,
                            "이수민" to BoardRole.MEMBER,
                        )
                }
            }

            When("보드가 삭제되면") {
                boardRepository.softDelete(board.id, owner.id)

                Then("초대를 찾을 수 없다") {
                    shouldThrow<NotFoundException> { queryService.get(issued.code, null) }
                        .errorCode shouldBe BoardErrorCode.INVITATION_NOT_FOUND
                }
            }

            When("발급자가 탈퇴하면") {
                userRepository.withdraw(owner.withdraw(Instant.now()))

                Then("초대를 찾을 수 없다") {
                    shouldThrow<NotFoundException> { queryService.get(issued.code, null) }
                        .errorCode shouldBe BoardErrorCode.INVITATION_NOT_FOUND
                }
            }
        }

        Given("만료된 초대가") {
            val owner = userRepository.saveTestUser()
            val board = boardRepository.save(owner.id)
            val invitation =
                invitationRepository.save(
                    boardId = board.id,
                    inviterUserId = owner.id,
                    code = "ABC234",
                    expiresAt = Instant.now().minusSeconds(1),
                )

            When("조회되면") {
                Then("초대를 찾을 수 없다") {
                    shouldThrow<NotFoundException> { queryService.get(invitation.code, null) }
                        .errorCode shouldBe BoardErrorCode.INVITATION_NOT_FOUND
                }
            }
        }
    })
