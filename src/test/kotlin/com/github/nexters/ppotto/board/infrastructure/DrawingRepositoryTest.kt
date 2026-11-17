package com.github.nexters.ppotto.board.infrastructure

import com.github.nexters.ppotto.board.application.model.DrawingLayoutDeleteCommand
import com.github.nexters.ppotto.board.application.model.DrawingLayoutUpdateCommand
import com.github.nexters.ppotto.board.domain.Drawing
import com.github.nexters.ppotto.board.domain.DrawingScope
import com.github.nexters.ppotto.board.domain.NewDrawing
import com.github.nexters.ppotto.board.support.newDrawing
import com.github.nexters.ppotto.board.support.newTextDrawing
import com.github.nexters.ppotto.board.support.uuidV7
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.identifier.DrawingId
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class DrawingRepositoryTest(
    drawingRepository: DrawingRepository,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        Given("보드에 드로잉을 저장한 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val drawingId = DrawingId(uuidV7())
            val original =
                NewDrawing.Stroke(
                    id = drawingId,
                    boardId = board.id,
                    stickerId = null,
                    scope = DrawingScope.BOARD,
                    zIndex = 0,
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
                    val stroke = drawings.single().shouldBeInstanceOf<Drawing.Stroke>()
                    stroke.id shouldBe drawingId
                    stroke.color shouldBe "#FFD400"
                    stroke.strokeWidth shouldBe 4.0
                    stroke.stroke["points"] shouldBe listOf(listOf(1.0, 2.0))
                }
            }

            When("소프트 삭제하면") {
                drawingRepository.softDeleteByIds(board.id, listOf(drawingId))

                Then("활성 드로잉 조회에서 제외된다") {
                    drawingRepository.findByBoardId(board.id) shouldHaveSize 0
                }
            }
        }

        Given("보드에 텍스트를 저장한 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val textId = DrawingId(uuidV7())
            drawingRepository.upsertAll(listOf(newTextDrawing(boardId = board.id, id = textId, zIndex = 7)))

            When("보드의 드로잉을 조회하면") {
                val drawings = drawingRepository.findByBoardId(board.id)

                Then("텍스트 타입으로 복원된다") {
                    val text = drawings.single().shouldBeInstanceOf<Drawing.Text>()
                    text.id shouldBe textId
                    text.content shouldBe "여름 휴가"
                    text.fontSize shouldBe 26.0
                    text.maxWidth shouldBe 280.0
                    text.zIndex shouldBe 7
                }
            }

            When("같은 아이디를 선으로 다시 저장하면") {
                drawingRepository.upsertAll(listOf(newDrawing(boardId = board.id, id = textId)))

                Then("선으로 바뀌고 텍스트 컬럼은 남지 않는다") {
                    drawingRepository
                        .findByBoardId(board.id)
                        .single()
                        .shouldBeInstanceOf<Drawing.Stroke>()
                }
            }
        }

        Given("v2에서 새 선과 텍스트를 저장하면") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val stroke = newDrawing(boardId = board.id)
            val text = newTextDrawing(boardId = board.id)
            drawingRepository.insertAll(listOf(stroke, text))

            When("조회한 버전으로 기존 항목을 수정하면") {
                val strokeUpdated =
                    drawingRepository.updateVersioned(DrawingLayoutUpdateCommand(stroke.copy(color = "#FFD400"), 0))
                val textUpdated =
                    drawingRepository.updateVersioned(DrawingLayoutUpdateCommand(text.copy(posX = 100.0), 0))

                Then("두 항목의 배치와 버전이 각각 갱신된다") {
                    strokeUpdated shouldBe true
                    textUpdated shouldBe true
                    val drawings = drawingRepository.findByBoardId(board.id).associateBy { it.id }
                    drawings.getValue(stroke.id).color shouldBe "#FFD400"
                    drawings.getValue(stroke.id).layoutVersion shouldBe 1
                    drawings
                        .getValue(text.id)
                        .shouldBeInstanceOf<Drawing.Text>()
                        .posX shouldBe 100.0
                    drawings.getValue(text.id).layoutVersion shouldBe 1
                }
            }

            When("오래된 버전으로 수정하거나 삭제하면") {
                val staleUpdate = drawingRepository.updateVersioned(DrawingLayoutUpdateCommand(stroke, 1))
                val staleDelete = drawingRepository.softDeleteVersioned(board.id, DrawingLayoutDeleteCommand(text.id, 1))

                Then("두 요청 모두 데이터를 바꾸지 않는다") {
                    staleUpdate shouldBe false
                    staleDelete shouldBe false
                    drawingRepository.findByBoardId(board.id) shouldHaveSize 2
                }
            }

            When("삭제한 ID를 다시 생성하려고 하면") {
                drawingRepository.softDeleteByIds(board.id, listOf(stroke.id))

                Then("삭제된 항목을 되살리지 않는다") {
                    shouldThrow<InvalidInputException> { drawingRepository.insertAll(listOf(stroke)) }
                    shouldThrow<InvalidInputException> { drawingRepository.upsertAll(listOf(stroke)) }
                    drawingRepository.findByBoardId(board.id) shouldHaveSize 1
                }
            }
        }
    })
