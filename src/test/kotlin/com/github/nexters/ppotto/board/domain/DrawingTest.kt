package com.github.nexters.ppotto.board.domain

import com.github.nexters.ppotto.board.support.uuidV7
import com.github.nexters.ppotto.global.error.CommonErrorCode
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.DrawingId
import com.github.nexters.ppotto.global.identifier.StickerId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

class DrawingTest :
    BehaviorSpec({
        Given("스티커 범위 드로잉에 stickerId가 없으면") {
            When("드로잉 생성 모델을 만들면") {
                val exception = shouldThrow<InvalidInputException> { newStroke(DrawingScope.STICKER, null) }

                Then("COMMON-001로 거부한다") {
                    exception.errorCode shouldBe CommonErrorCode.INVALID_INPUT
                }
            }
        }

        Given("보드 범위 드로잉에 stickerId가 있으면") {
            When("드로잉 생성 모델을 만들면") {
                val exception =
                    shouldThrow<InvalidInputException> { newStroke(DrawingScope.BOARD, StickerId(UUID.randomUUID())) }

                Then("COMMON-001로 거부한다") {
                    exception.errorCode shouldBe CommonErrorCode.INVALID_INPUT
                }
            }
        }

        Given("클라이언트가 uuidv7이 아닌 아이디를 보내면") {
            When("드로잉 생성 모델을 만들면") {
                val exception =
                    shouldThrow<InvalidInputException> {
                        newStroke(DrawingScope.BOARD, null, DrawingId(UUID.randomUUID()))
                    }

                Then("COMMON-001로 거부한다") {
                    exception.errorCode shouldBe CommonErrorCode.INVALID_INPUT
                }
            }
        }

        Given("선 굵기가 0 이하이면") {
            When("드로잉 생성 모델을 만들면") {
                val exception =
                    shouldThrow<InvalidInputException> { newStroke(DrawingScope.BOARD, null, strokeWidth = 0.0) }

                Then("COMMON-001로 거부한다") {
                    exception.errorCode shouldBe CommonErrorCode.INVALID_INPUT
                }
            }
        }

        Given("문구가 32자를 넘으면") {
            When("텍스트 생성 모델을 만들면") {
                val exception = shouldThrow<InvalidInputException> { newText("가".repeat(33)) }

                Then("COMMON-001로 거부한다") {
                    exception.errorCode shouldBe CommonErrorCode.INVALID_INPUT
                }
            }
        }

        Given("범위와 stickerId가 일치하면") {
            When("드로잉 생성 모델을 만들면") {
                val drawing = newStroke(DrawingScope.BOARD, null)

                Then("입력한 범위를 유지한다") {
                    drawing.scope shouldBe DrawingScope.BOARD
                }
            }
        }
    })

private fun newStroke(
    scope: DrawingScope,
    stickerId: StickerId?,
    id: DrawingId = DrawingId(uuidV7()),
    strokeWidth: Double = 2.0,
) = NewDrawing.Stroke(
    id = id,
    boardId = BoardId(UUID.randomUUID()),
    stickerId = stickerId,
    scope = scope,
    zIndex = 0,
    stroke = mapOf("points" to listOf(listOf(1.0, 2.0))),
    color = "#FFFFFF",
    strokeWidth = strokeWidth,
)

private fun newText(content: String) =
    NewDrawing.Text(
        id = DrawingId(uuidV7()),
        boardId = BoardId(UUID.randomUUID()),
        stickerId = null,
        scope = DrawingScope.BOARD,
        color = "#FFFFFF",
        zIndex = 0,
        content = content,
        fontSize = 26.0,
        posX = 80.0,
        posY = 290.5,
        maxWidth = 280.0,
        rotation = 0.0,
    )
