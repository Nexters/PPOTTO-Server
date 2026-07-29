package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.util.UUID

class BoardQueryServiceTest(
    private val boardQueryService: BoardQueryService,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        Given("Board가 등록된 상태에서") {
            val board = boardRepository.save(userRepository.save().id)

            When("저장된 아이디로 조회하면") {
                Then("해당 Board를 반환한다") {
                    boardQueryService.getById(board.id).id shouldBe board.id
                }
            }
        }

        Given("존재하지 않는 boardId로") {
            When("조회하면") {
                Then("NotFoundException(BOARD-002)이 발생한다") {
                    val exception = shouldThrow<NotFoundException> { boardQueryService.getById(UUID.randomUUID()) }
                    exception.errorCode.code shouldBe "BOARD-002"
                    exception.errorCode shouldBe BoardErrorCode.NOT_FOUND
                }
            }
        }
    })
