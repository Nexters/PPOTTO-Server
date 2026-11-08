package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.domain.BoardShareScope
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.infrastructure.DrawingRepository
import com.github.nexters.ppotto.board.support.BoardTestConfig
import com.github.nexters.ppotto.board.support.FakeBoardStickerPort
import com.github.nexters.ppotto.board.support.addBoardMember
import com.github.nexters.ppotto.board.support.boardStickerItem
import com.github.nexters.ppotto.board.support.newTextDrawing
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.jooq.tables.references.BOARD_MEMBERS
import com.github.nexters.ppotto.jooq.tables.references.USERS
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import org.springframework.context.annotation.Import
import java.time.Instant

@Import(BoardTestConfig::class)
class BoardShareQueryServiceTest(
    boardShareQueryService: BoardShareQueryService,
    boardShareService: BoardShareService,
    boardRepository: BoardRepository,
    drawingRepository: DrawingRepository,
    userRepository: UserRepository,
    stickerPort: FakeBoardStickerPort,
    dslContext: DSLContext,
) : IntegrationTest({
        Given("방장과 참여자가 있는 공동 보드를 참여자가 공유했을 때") {
            val owner = userRepository.saveTestUser("김용희")
            val member = userRepository.saveTestUser("이수민")
            val board = boardRepository.save(owner.id, "제주 여행")
            dslContext.addBoardMember(board.id, member.id)
            val ownerSticker = boardStickerItem()
            val memberSticker = boardStickerItem()
            stickerPort.stickersByBoardId[board.id] = listOf(ownerSticker, memberSticker)
            drawingRepository.upsertAll(listOf(newTextDrawing(board.id)))
            val share = boardShareService.share(board.id, member.id, BoardShareScope.RECAP)

            When("공유 토큰으로 조회하면") {
                val result = boardShareQueryService.getSharedBoard(share.shareToken)

                Then("보드 이름, 공유자 이름, 범위와 함께 모든 참여자의 스티커와 그림을 반환한다") {
                    result.boardName shouldBe "제주 여행"
                    result.sharerName shouldBe "이수민"
                    result.scope shouldBe BoardShareScope.RECAP
                    result.stickers.map { it.id } shouldBe listOf(ownerSticker.id, memberSticker.id)
                    result.drawings shouldHaveSize 1
                }

                Then("스티커의 읽음 표시는 항상 꺼서 내려준다") {
                    result.stickers
                        .map { it.isNew }
                        .distinct() shouldBe listOf(false)
                }
            }

            When("공유자가 범위를 바꾼 뒤 같은 토큰으로 조회하면") {
                boardShareService.share(board.id, member.id, BoardShareScope.PHOTOS)
                val result = boardShareQueryService.getSharedBoard(share.shareToken)

                Then("바뀐 범위가 즉시 반영된다") {
                    result.scope shouldBe BoardShareScope.PHOTOS
                }
            }

            When("보드 이름이 바뀐 뒤 조회하면") {
                boardRepository.updateName(board.id, owner.id, "부산 여행")
                val result = boardShareQueryService.getSharedBoard(share.shareToken)

                Then("현재 보드 이름을 반환한다") {
                    result.boardName shouldBe "부산 여행"
                }
            }

            When("공유자가 보드에서 나간 뒤 조회하면") {
                dslContext
                    .deleteFrom(BOARD_MEMBERS)
                    .where(BOARD_MEMBERS.BOARD_ID.eq(board.id))
                    .and(BOARD_MEMBERS.USER_ID.eq(member.id))
                    .execute()

                Then("공유된 보드를 찾을 수 없다") {
                    val exception = shouldThrow<NotFoundException> { boardShareQueryService.getSharedBoard(share.shareToken) }
                    exception.errorCode shouldBe BoardErrorCode.SHARE_NOT_FOUND
                }
            }

            When("공유자가 회원 탈퇴한 뒤 조회하면") {
                dslContext
                    .update(USERS)
                    .set(USERS.DELETED_AT, Instant.now())
                    .where(USERS.ID.eq(member.id))
                    .execute()

                Then("공유된 보드를 찾을 수 없다") {
                    val exception = shouldThrow<NotFoundException> { boardShareQueryService.getSharedBoard(share.shareToken) }
                    exception.errorCode shouldBe BoardErrorCode.SHARE_NOT_FOUND
                }
            }

            When("보드가 삭제된 뒤 조회하면") {
                boardRepository.softDelete(board.id, owner.id)

                Then("공유된 보드를 찾을 수 없다") {
                    val exception = shouldThrow<NotFoundException> { boardShareQueryService.getSharedBoard(share.shareToken) }
                    exception.errorCode shouldBe BoardErrorCode.SHARE_NOT_FOUND
                }
            }
        }

        Given("스티커가 없는 보드를 공유했을 때") {
            val owner = userRepository.saveTestUser()
            val board = boardRepository.save(owner.id, "빈 보드")
            val share = boardShareService.share(board.id, owner.id, BoardShareScope.BOARD)

            When("조회하면") {
                val result = boardShareQueryService.getSharedBoard(share.shareToken)

                Then("빈 스티커와 그림 목록을 반환한다") {
                    result.stickers.shouldBeEmpty()
                    result.drawings.shouldBeEmpty()
                }
            }
        }

        Given("발급된 적 없는 토큰으로") {
            When("조회하면") {
                Then("공유된 보드를 찾을 수 없다") {
                    val exception = shouldThrow<NotFoundException> { boardShareQueryService.getSharedBoard("unknown-token") }
                    exception.errorCode shouldBe BoardErrorCode.SHARE_NOT_FOUND
                }
            }
        }
    })
