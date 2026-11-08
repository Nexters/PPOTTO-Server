package com.github.nexters.ppotto.board.infrastructure

import com.github.nexters.ppotto.board.domain.BoardShareScope
import com.github.nexters.ppotto.jooq.tables.references.BOARDS
import com.github.nexters.ppotto.jooq.tables.references.BOARD_SHARES
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.support.TransactionTemplate

class BoardShareRepositoryTest(
    boardShareRepository: BoardShareRepository,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
    transactionTemplate: TransactionTemplate,
    dslContext: DSLContext,
) : IntegrationTest({
        Given("보드 공유가 저장되어 있을 때") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)
            val saved = boardShareRepository.save(board.id, user.id, "share-token", BoardShareScope.RECAP)

            When("토큰, 보드와 공유자로 조회하면") {
                val byToken = boardShareRepository.findByShareToken(saved.shareToken)
                val byBoardAndSharerWithoutLock = boardShareRepository.findByBoardIdAndSharerUserId(board.id, user.id)
                val byBoardAndSharer =
                    transactionTemplate.execute {
                        boardShareRepository.findByBoardIdAndSharerUserIdForUpdate(board.id, user.id)
                    }

                Then("같은 공유를 반환한다") {
                    byToken shouldBe saved
                    byBoardAndSharerWithoutLock shouldBe saved
                    byBoardAndSharer shouldBe saved
                }
            }

            When("없는 토큰으로 조회하면") {
                val found = boardShareRepository.findByShareToken("unknown-token")

                Then("null을 반환한다") {
                    found.shouldBeNull()
                }
            }
        }

        Given("공유 범위를 바꿀 보드 공유가 있을 때") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)
            val saved = boardShareRepository.save(board.id, user.id, "share-token", BoardShareScope.RECAP)

            When("범위를 바꾸면") {
                val updated = boardShareRepository.updateScope(board.id, user.id, BoardShareScope.PHOTOS)

                Then("토큰은 유지하고 범위만 바꾼다") {
                    updated.shareToken shouldBe saved.shareToken
                    updated.scope shouldBe BoardShareScope.PHOTOS
                    boardShareRepository.findByShareToken(saved.shareToken)?.scope shouldBe BoardShareScope.PHOTOS
                }
            }
        }

        Given("같은 보드를 여러 사용자가 공유할 때") {
            val owner = userRepository.saveTestUser()
            val member = userRepository.saveTestUser()
            val board = boardRepository.save(owner.id)
            val ownerShare = boardShareRepository.save(board.id, owner.id, "owner-token", BoardShareScope.RECAP)
            val memberShare = boardShareRepository.save(board.id, member.id, "member-token", BoardShareScope.BOARD)

            When("한 사용자의 범위를 바꾸면") {
                boardShareRepository.updateScope(board.id, member.id, BoardShareScope.PHOTOS)

                Then("다른 사용자의 공유는 바뀌지 않는다") {
                    boardShareRepository.findByShareToken(ownerShare.shareToken) shouldBe ownerShare
                    boardShareRepository.findByShareToken(memberShare.shareToken)?.scope shouldBe BoardShareScope.PHOTOS
                }
            }
        }

        Given("같은 보드에서 같은 공유자의 공유가 이미 있을 때") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)
            boardShareRepository.save(board.id, user.id, "share-token", BoardShareScope.RECAP)

            When("다른 토큰으로 공유를 하나 더 저장하면") {
                Then("보드와 공유자 유니크 제약으로 거부한다") {
                    shouldThrow<DataIntegrityViolationException> {
                        boardShareRepository.save(board.id, user.id, "other-token", BoardShareScope.RECAP)
                    }
                }
            }
        }

        Given("같은 공유 토큰이 이미 있을 때") {
            val firstUser = userRepository.saveTestUser()
            val secondUser = userRepository.saveTestUser()
            val firstBoard = boardRepository.save(firstUser.id)
            val secondBoard = boardRepository.save(secondUser.id)
            boardShareRepository.save(firstBoard.id, firstUser.id, "share-token", BoardShareScope.RECAP)

            When("다른 공유에 같은 토큰을 저장하면") {
                Then("토큰 유니크 제약으로 거부한다") {
                    shouldThrow<DataIntegrityViolationException> {
                        boardShareRepository.save(secondBoard.id, secondUser.id, "share-token", BoardShareScope.RECAP)
                    }
                }
            }
        }

        Given("허용하지 않는 공유 범위 값이 있을 때") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)

            When("공유를 저장하면") {
                Then("범위 CHECK 제약으로 거부한다") {
                    shouldThrow<DataIntegrityViolationException> {
                        dslContext
                            .insertInto(
                                BOARD_SHARES,
                                BOARD_SHARES.BOARD_ID,
                                BOARD_SHARES.SHARER_USER_ID,
                                BOARD_SHARES.SHARE_TOKEN,
                                BOARD_SHARES.SCOPE,
                            ).values(board.id, user.id, "share-token", "ALL")
                            .execute()
                    }
                }
            }
        }

        Given("공유가 연결된 보드가 있을 때") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)
            val share = boardShareRepository.save(board.id, user.id, "share-token", BoardShareScope.RECAP)

            When("보드를 하드 삭제하면") {
                dslContext
                    .deleteFrom(BOARDS)
                    .where(BOARDS.ID.eq(board.id))
                    .execute()

                Then("연결된 공유도 CASCADE로 삭제된다") {
                    boardShareRepository.findByShareToken(share.shareToken).shouldBeNull()
                }
            }
        }
    })
