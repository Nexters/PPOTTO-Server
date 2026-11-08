package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.domain.BoardShareScope
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.infrastructure.BoardShareRepository
import com.github.nexters.ppotto.board.support.addBoardMember
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.jooq.tables.references.BOARD_SHARES
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.runConcurrently
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.jooq.DSLContext

class BoardShareServiceTest(
    boardShareService: BoardShareService,
    boardShareRepository: BoardShareRepository,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
    dslContext: DSLContext,
) : IntegrationTest({
        Given("보드 방장이") {
            val owner = userRepository.saveTestUser()
            val board = boardRepository.save(owner.id, "제주 여행")

            When("처음 공유하면") {
                val result = boardShareService.share(board.id, owner.id, BoardShareScope.RECAP)

                Then("방장 이름의 공유 링크를 요청한 범위로 저장한다") {
                    result.scope shouldBe BoardShareScope.RECAP
                    val stored = boardShareRepository.findByShareToken(result.shareToken)!!
                    stored.boardId shouldBe board.id
                    stored.sharerUserId shouldBe owner.id
                    stored.scope shouldBe BoardShareScope.RECAP
                }
            }

            When("다른 범위로 다시 공유하면") {
                val first = boardShareService.share(board.id, owner.id, BoardShareScope.RECAP)
                val second = boardShareService.share(board.id, owner.id, BoardShareScope.PHOTOS)

                Then("같은 토큰을 유지하고 범위만 바꾼다") {
                    second.shareToken shouldBe first.shareToken
                    second.scope shouldBe BoardShareScope.PHOTOS
                    boardShareRepository.findByShareToken(first.shareToken)?.scope shouldBe BoardShareScope.PHOTOS
                }
            }

            When("같은 사용자가 동시에 공유하면") {
                val results = runConcurrently(8) { boardShareService.share(board.id, owner.id, BoardShareScope.RECAP) }

                Then("모든 요청이 같은 토큰을 반환하고 공유는 한 건만 저장된다") {
                    results.map { it.getOrThrow().shareToken }.distinct() shouldHaveSize 1
                    dslContext.fetchCount(BOARD_SHARES, BOARD_SHARES.BOARD_ID.eq(board.id)) shouldBe 1
                }
            }
        }

        Given("방장과 참여자가 있는 공동 보드에서") {
            val owner = userRepository.saveTestUser()
            val member = userRepository.saveTestUser()
            val board = boardRepository.save(owner.id, "제주 여행")
            dslContext.addBoardMember(board.id, member.id)

            When("두 사람이 각자 공유하면") {
                val ownerShare = boardShareService.share(board.id, owner.id, BoardShareScope.RECAP)
                val memberShare = boardShareService.share(board.id, member.id, BoardShareScope.BOARD)

                Then("사람마다 다른 토큰과 범위를 가진다") {
                    memberShare.shareToken shouldNotBe ownerShare.shareToken
                    boardShareRepository.findByShareToken(memberShare.shareToken)?.sharerUserId shouldBe member.id
                }
            }

            When("참여자가 범위를 바꾸면") {
                val ownerShare = boardShareService.share(board.id, owner.id, BoardShareScope.RECAP)
                boardShareService.share(board.id, member.id, BoardShareScope.BOARD)
                boardShareService.share(board.id, member.id, BoardShareScope.PHOTOS)

                Then("방장의 공유 범위는 바뀌지 않는다") {
                    boardShareRepository.findByShareToken(ownerShare.shareToken)?.scope shouldBe BoardShareScope.RECAP
                }
            }
        }

        Given("보드와 무관한 사용자가") {
            val owner = userRepository.saveTestUser()
            val stranger = userRepository.saveTestUser()
            val board = boardRepository.save(owner.id, "제주 여행")

            When("공유하면") {
                Then("보드 존재를 노출하지 않고 404로 거부한다") {
                    val exception =
                        shouldThrow<NotFoundException> {
                            boardShareService.share(board.id, stranger.id, BoardShareScope.RECAP)
                        }
                    exception.errorCode shouldBe BoardErrorCode.NOT_FOUND
                    dslContext.fetchCount(BOARD_SHARES, BOARD_SHARES.BOARD_ID.eq(board.id)) shouldBe 0
                }
            }
        }
    })
