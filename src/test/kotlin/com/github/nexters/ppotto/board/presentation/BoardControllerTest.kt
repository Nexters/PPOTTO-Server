package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.presentation.dto.CreateBoardRequest
import com.github.nexters.ppotto.board.presentation.dto.RenameBoardRequest
import com.github.nexters.ppotto.board.support.BoardTestConfig
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.rawId
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.springframework.context.annotation.Import

@Import(BoardTestConfig::class)
class BoardControllerTest(
    boardController: BoardController,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        Given("인증된 사용자가") {
            val user = userRepository.saveTestUser()

            When("보드를 생성하고 이름을 변경하면") {
                val created = boardController.create(user.rawId, CreateBoardRequest()).data!!
                val renamed = boardController.rename(user.rawId, created.id, RenameBoardRequest("여름 휴가")).data!!

                Then("목록과 상세 응답에 변경한 이름을 반환한다") {
                    renamed.name shouldBe "여름 휴가"
                    boardController
                        .list(user.rawId)
                        .data!!
                        .map { it.id } shouldContainExactly listOf(created.id)
                    boardController
                        .get(user.rawId, created.id)
                        .data!!
                        .name shouldBe "여름 휴가"
                }
            }
        }

        Given("다른 사용자의 보드가 있는 경우") {
            val owner = userRepository.saveTestUser()
            val board = boardRepository.save(owner.rawId)

            When("인증된 다른 사용자가 조회하면") {
                Then("성공 응답으로 노출되지 않는다") {
                    val other = userRepository.saveTestUser()
                    shouldThrow<NotFoundException> {
                        boardController.get(other.rawId, board.id)
                    }
                }
            }
        }
    })
