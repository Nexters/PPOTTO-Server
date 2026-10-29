package com.github.nexters.ppotto.board.infrastructure

import com.github.nexters.ppotto.jooq.tables.references.BOARDS
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant
import java.time.temporal.ChronoUnit

class BoardInvitationRepositoryTest(
    boardInvitationRepository: BoardInvitationRepository,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
    dslContext: DSLContext,
) : IntegrationTest({
        Given("보드 초대가 저장되어 있을 때") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)
            val expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
            val saved = boardInvitationRepository.save(board.id, user.id, "K7P2QX", expiresAt)

            When("아이디, 코드, 보드와 발급자로 조회하면") {
                val byId = boardInvitationRepository.findById(saved.id)
                val byCode = boardInvitationRepository.findByCode(saved.code)
                val byBoardAndInviter =
                    boardInvitationRepository.findByBoardIdAndInviterUserId(board.id, user.id)

                Then("같은 초대를 반환한다") {
                    byId shouldBe saved
                    byCode shouldBe saved
                    byBoardAndInviter shouldBe saved
                }
            }
        }

        Given("같은 보드에서 같은 발급자의 초대가 이미 있을 때") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)
            val expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
            boardInvitationRepository.save(board.id, user.id, "K7P2QX", expiresAt)

            When("다른 코드로 초대를 하나 더 저장하면") {
                Then("보드와 발급자 유니크 제약으로 거부한다") {
                    shouldThrow<DataIntegrityViolationException> {
                        boardInvitationRepository.save(board.id, user.id, "R8T3WY", expiresAt)
                    }
                }
            }
        }

        Given("같은 초대 코드가 이미 있을 때") {
            val firstUser = userRepository.saveTestUser()
            val secondUser = userRepository.saveTestUser()
            val firstBoard = boardRepository.save(firstUser.id)
            val secondBoard = boardRepository.save(secondUser.id)
            val expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
            boardInvitationRepository.save(firstBoard.id, firstUser.id, "K7P2QX", expiresAt)

            When("다른 초대에 같은 코드를 저장하면") {
                Then("코드 유니크 제약으로 거부한다") {
                    shouldThrow<DataIntegrityViolationException> {
                        boardInvitationRepository.save(secondBoard.id, secondUser.id, "K7P2QX", expiresAt)
                    }
                }
            }
        }

        Given("허용하지 않는 문자가 포함된 초대 코드가 있을 때") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)

            When("초대를 저장하면") {
                Then("코드 형식 CHECK 제약으로 거부한다") {
                    shouldThrow<DataIntegrityViolationException> {
                        boardInvitationRepository.save(
                            board.id,
                            user.id,
                            "I0O1AA",
                            Instant.now().plus(30, ChronoUnit.DAYS),
                        )
                    }
                }
            }
        }

        Given("초대가 연결된 보드가 있을 때") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)
            val invitation =
                boardInvitationRepository.save(
                    board.id,
                    user.id,
                    "K7P2QX",
                    Instant.now().plus(30, ChronoUnit.DAYS),
                )

            When("보드를 하드 삭제하면") {
                dslContext
                    .deleteFrom(BOARDS)
                    .where(BOARDS.ID.eq(board.id))
                    .execute()

                Then("연결된 초대도 CASCADE로 삭제된다") {
                    boardInvitationRepository.findById(invitation.id).shouldBeNull()
                }
            }
        }
    })
