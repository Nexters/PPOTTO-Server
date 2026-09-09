package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.presentation.dto.CreateBoardRequest
import com.github.nexters.ppotto.board.presentation.dto.RenameBoardRequest
import com.github.nexters.ppotto.board.support.BoardTestConfig
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.springframework.context.annotation.Import

@Import(BoardTestConfig::class)
class BoardControllerTest(
    boardController: BoardController,
    boardDetailController: BoardDetailController,
    boardDetailV2Controller: BoardDetailV2Controller,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        Given("보드를 하나 만든 인증된 사용자가") {
            val user = userRepository.saveTestUser()
            val created = boardController.create(user.id, CreateBoardRequest()).data!!

            When("보드 목록을 조회하면") {
                val boards = boardController.list(user.id).data!!

                Then("방금 만든 보드만 반환한다") {
                    boards.map { it.id } shouldContainExactly listOf(created.id)
                }
            }

            When("보드 이름을 변경하면") {
                val renamed = boardController.rename(user.id, created.id, RenameBoardRequest("여름 휴가")).data!!

                Then("변경한 이름을 응답한다") {
                    renamed.name shouldBe "여름 휴가"
                }

                Then("v1 상세 응답도 변경한 이름을 반환한다") {
                    boardDetailController
                        .get(user.id, created.id)
                        .data!!
                        .name shouldBe "여름 휴가"
                }

                Then("v2 상세 응답도 변경한 이름을 반환한다") {
                    boardDetailV2Controller
                        .get(user.id, created.id)
                        .data!!
                        .name shouldBe "여름 휴가"
                }
            }
        }

        Given("다른 사용자의 보드가 있는 경우") {
            val owner = userRepository.saveTestUser()
            val board = boardRepository.save(owner.id)
            val other = userRepository.saveTestUser()

            When("인증된 다른 사용자가 조회하면") {
                val exception = shouldThrow<NotFoundException> { boardDetailController.get(other.id, board.id) }

                Then("BOARD-002로 거부하고 보드를 노출하지 않는다") {
                    exception.errorCode.code shouldBe "BOARD-002"
                }
            }
        }
    })
