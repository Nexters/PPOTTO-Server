package com.github.nexters.ppotto.board.infrastructure

import com.github.nexters.ppotto.board.domain.DrawingScope
import com.github.nexters.ppotto.board.domain.NewDrawing
import com.github.nexters.ppotto.board.support.uuidV7
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.rawId
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class DrawingRepositoryTest(
    drawingRepository: DrawingRepository,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        Given("보드에 드로잉을 저장한 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().rawId)
            val drawingId = uuidV7()
            val original =
                NewDrawing(
                    id = drawingId,
                    boardId = board.id,
                    stickerId = null,
                    scope = DrawingScope.BOARD,
                    stroke = mapOf("points" to listOf(listOf(1.0, 2.0))),
                    color = "#FFFFFF",
                    strokeWidth = 2.0,
                )
            drawingRepository.upsertAll(listOf(original))

            When("같은 아이디로 다시 저장하면") {
                drawingRepository.upsertAll(
                    listOf(
                        original.copy(
                            color = "#FFD400",
                            strokeWidth = 4.0,
                        ),
                    ),
                )

                Then("중복 없이 내용이 갱신된다") {
                    val drawings = drawingRepository.findByBoardId(board.id)
                    drawings shouldHaveSize 1
                    drawings.single().id shouldBe drawingId
                    drawings.single().color shouldBe "#FFD400"
                    drawings.single().strokeWidth shouldBe 4.0
                    drawings.single().stroke["points"] shouldBe listOf(listOf(1.0, 2.0))
                }
            }

            When("소프트 삭제하면") {
                drawingRepository.softDeleteByIds(board.id, listOf(drawingId))

                Then("활성 드로잉 조회에서 제외된다") {
                    drawingRepository.findByBoardId(board.id) shouldHaveSize 0
                }
            }
        }
    })
