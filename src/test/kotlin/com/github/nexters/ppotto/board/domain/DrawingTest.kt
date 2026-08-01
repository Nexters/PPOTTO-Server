package com.github.nexters.ppotto.board.domain

import com.github.nexters.ppotto.board.support.uuidV7
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
                Then("도메인 불변식 검증에 실패한다") {
                    shouldThrow<IllegalArgumentException> {
                        newDrawing(DrawingScope.STICKER, null)
                    }
                }
            }
        }

        Given("보드 범위 드로잉에 stickerId가 있으면") {
            When("드로잉 생성 모델을 만들면") {
                Then("도메인 불변식 검증에 실패한다") {
                    shouldThrow<IllegalArgumentException> {
                        newDrawing(DrawingScope.BOARD, StickerId(UUID.randomUUID()))
                    }
                }
            }
        }

        Given("범위와 stickerId가 일치하면") {
            When("드로잉 생성 모델을 만들면") {
                val drawing = newDrawing(DrawingScope.BOARD, null)

                Then("입력한 범위를 유지한다") {
                    drawing.scope shouldBe DrawingScope.BOARD
                }
            }
        }
    })

private fun newDrawing(
    scope: DrawingScope,
    stickerId: StickerId?,
) = NewDrawing(
    id = DrawingId(uuidV7()),
    boardId = BoardId(UUID.randomUUID()),
    stickerId = stickerId,
    scope = scope,
    stroke = mapOf("points" to listOf(listOf(1.0, 2.0))),
    color = "#FFFFFF",
    strokeWidth = 2.0,
)
