package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.support.addBoardMember
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.support.TransactionTemplate

class BoardAccessServiceTest(
    boardAccessService: BoardAccessService,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
    transactionTemplate: TransactionTemplate,
    dslContext: DSLContext,
) : IntegrationTest({
        Given("사용자가 소유한 보드가 있을 때") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)

            When("호출부 트랜잭션 없이 행 잠금 조회를 호출하면") {
                Then("잠금이 곧바로 풀리지 않도록 트랜잭션 필수 위반으로 거부한다") {
                    shouldThrow<IllegalTransactionStateException> {
                        boardAccessService.getOwnedByIdForUpdate(board.id, user.id)
                    }
                }
            }

            When("호출부 트랜잭션 안에서 행 잠금 조회를 호출하면") {
                Then("호출부 트랜잭션에 참여해 소유한 보드를 반환한다") {
                    transactionTemplate
                        .execute { boardAccessService.getOwnedByIdForUpdate(board.id, user.id) }
                        .id shouldBe board.id
                }
            }

            When("호출부 트랜잭션 안에서 소유자가 아닌 사용자가 행 잠금 조회를 호출하면") {
                val stranger = userRepository.saveTestUser()

                Then("BOARD-002로 거부한다") {
                    shouldThrow<NotFoundException> {
                        transactionTemplate.execute { boardAccessService.getOwnedByIdForUpdate(board.id, stranger.id) }
                    }.errorCode shouldBe BoardErrorCode.NOT_FOUND
                }
            }
        }

        Given("공동 보드가 있을 때") {
            val owner = userRepository.saveTestUser()
            val member = userRepository.saveTestUser()
            val stranger = userRepository.saveTestUser()
            val board = boardRepository.save(owner.id)
            dslContext.addBoardMember(board.id, member.id)

            When("방장이 접근 권한을 확인하면") {
                Then("보드를 반환한다") {
                    boardAccessService.getAccessibleById(board.id, owner.id).id shouldBe board.id
                }
            }

            When("참여자가 접근 권한을 확인하면") {
                Then("보드를 반환한다") {
                    boardAccessService.getAccessibleById(board.id, member.id).id shouldBe board.id
                }
            }

            When("참여하지 않은 사용자가 접근 권한을 확인하면") {
                Then("BOARD-002로 거부한다") {
                    shouldThrow<NotFoundException> {
                        boardAccessService.getAccessibleById(board.id, stranger.id)
                    }.errorCode shouldBe BoardErrorCode.NOT_FOUND
                }
            }

            When("호출부 트랜잭션 없이 접근 가능한 보드의 행 잠금 조회를 호출하면") {
                Then("트랜잭션 필수 위반으로 거부한다") {
                    shouldThrow<IllegalTransactionStateException> {
                        boardAccessService.getAccessibleByIdForUpdate(board.id, member.id)
                    }
                }
            }

            When("호출부 트랜잭션 안에서 참여자가 접근 가능한 보드의 행 잠금 조회를 호출하면") {
                Then("보드를 반환한다") {
                    transactionTemplate
                        .execute { boardAccessService.getAccessibleByIdForUpdate(board.id, member.id) }
                        .id shouldBe board.id
                }
            }

            When("삭제된 보드의 접근 권한을 확인하면") {
                boardRepository.softDelete(board.id, owner.id)

                Then("방장과 참여자 모두 BOARD-002로 거부한다") {
                    listOf(owner.id, member.id).forEach { userId ->
                        shouldThrow<NotFoundException> {
                            boardAccessService.getAccessibleById(board.id, userId)
                        }.errorCode shouldBe BoardErrorCode.NOT_FOUND
                    }
                }
            }
        }
    })
