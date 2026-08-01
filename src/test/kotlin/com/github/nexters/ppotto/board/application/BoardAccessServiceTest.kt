package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.rawId
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.support.TransactionTemplate

class BoardAccessServiceTest(
    boardAccessService: BoardAccessService,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
    transactionTemplate: TransactionTemplate,
) : IntegrationTest({
        Given("사용자가 소유한 보드가 있을 때") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.rawId)

            When("호출부 트랜잭션 없이 행 잠금 조회를 호출하면") {
                Then("잠금이 곧바로 풀리지 않도록 트랜잭션 필수 위반으로 거부한다") {
                    shouldThrow<IllegalTransactionStateException> {
                        boardAccessService.getOwnedByIdForUpdate(board.id, user.rawId)
                    }
                }
            }

            When("호출부 트랜잭션 안에서 행 잠금 조회를 호출하면") {
                Then("호출부 트랜잭션에 참여해 소유한 보드를 반환한다") {
                    transactionTemplate
                        .execute { boardAccessService.getOwnedByIdForUpdate(board.id, user.rawId) }
                        .id shouldBe board.id
                }
            }

            When("호출부 트랜잭션 안에서 소유자가 아닌 사용자가 행 잠금 조회를 호출하면") {
                val stranger = userRepository.saveTestUser()

                Then("BOARD-002로 거부한다") {
                    shouldThrow<NotFoundException> {
                        transactionTemplate.execute { boardAccessService.getOwnedByIdForUpdate(board.id, stranger.rawId) }
                    }.errorCode shouldBe BoardErrorCode.NOT_FOUND
                }
            }
        }
    })
