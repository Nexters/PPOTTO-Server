package com.github.nexters.ppotto.board.presentation.dto

import com.github.nexters.ppotto.board.application.BoardDetail
import com.github.nexters.ppotto.board.application.DrawingCreateCommand
import com.github.nexters.ppotto.board.domain.Drawing
import com.github.nexters.ppotto.board.domain.DrawingScope
import com.github.nexters.ppotto.board.support.boardStickerItem
import com.github.nexters.ppotto.board.support.uuidV7
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.DrawingId
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Instant
import java.util.UUID

private val BOARD_ID = BoardId(UUID.randomUUID())
private val STROKE_ID = DrawingId(uuidV7())
private val TEXT_ID = DrawingId(uuidV7())

private val STROKE =
    Drawing.Stroke(
        id = STROKE_ID,
        boardId = BOARD_ID,
        stickerId = null,
        scope = DrawingScope.BOARD,
        color = "#FFD400",
        zIndex = 6,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        stroke = mapOf("points" to listOf(listOf(1.0, 2.0))),
        strokeWidth = 4.0,
    )

private val TEXT =
    Drawing.Text(
        id = TEXT_ID,
        boardId = BOARD_ID,
        stickerId = null,
        scope = DrawingScope.BOARD,
        color = "#FFFFFF",
        zIndex = 7,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        content = "여름 휴가",
        fontSize = 26.0,
        posX = 80.0,
        posY = 290.5,
        maxWidth = 280.0,
        rotation = 0.0,
    )

private val BOARD_DETAIL =
    BoardDetail(
        id = BOARD_ID,
        name = "Board 7",
        stickers = listOf(boardStickerItem()),
        drawings = listOf(STROKE, TEXT),
    )

class BoardDetailResponseTest :
    BehaviorSpec({
        Given("선 하나와 텍스트 하나가 놓인 보드가") {
            When("v1 응답으로 변환되면") {
                val response = BoardDetailResponse.from(BOARD_DETAIL)

                Then("텍스트는 빠지고 선만 내려간다") {
                    response.drawings shouldHaveSize 1
                    response.drawings
                        .single()
                        .id shouldBe STROKE_ID
                }

                Then("겹침 순서가 stroke JSON 안의 zIndex 키로 합쳐진다") {
                    response.drawings
                        .single()
                        .stroke shouldBe
                        mapOf(
                            "points" to listOf(listOf(1.0, 2.0)),
                            "zIndex" to 6,
                        )
                }
            }

            When("v2 응답으로 변환되면") {
                val response = BoardDetailV2Response.from(BOARD_DETAIL)

                Then("선과 텍스트가 모두 내려간다") {
                    response.drawings.map { it.id } shouldContainExactly listOf(STROKE_ID, TEXT_ID)
                }

                Then("겹침 순서는 stroke JSON 이 아니라 zIndex 필드로 내려간다") {
                    val stroke =
                        response.drawings
                            .first()
                            .shouldBeInstanceOf<DrawingV2Response.Stroke>()
                    stroke.zIndex shouldBe 6
                    stroke.stroke shouldBe mapOf("points" to listOf(listOf(1.0, 2.0)))
                }

                Then("텍스트는 문구와 배치를 그대로 담는다") {
                    val text =
                        response.drawings
                            .last()
                            .shouldBeInstanceOf<DrawingV2Response.Text>()
                    text.content shouldBe "여름 휴가"
                    text.fontSize shouldBe 26.0
                    text.maxWidth shouldBe 280.0
                }
            }
        }

        Given("v1 클라이언트가 stroke JSON 안에 zIndex 를 담아 보내면") {
            val request =
                DrawingCreateRequest(
                    id = STROKE_ID,
                    scope = DrawingScope.BOARD,
                    stickerId = null,
                    stroke = mapOf("points" to listOf(listOf(1.0, 2.0)), "zIndex" to 6),
                    color = "#FFD400",
                    strokeWidth = 4.0,
                )

            When("커맨드로 변환되면") {
                val command = request.toCommand().shouldBeInstanceOf<DrawingCreateCommand.Stroke>()

                Then("zIndex 는 정식 필드로 옮겨진다") {
                    command.zIndex shouldBe 6
                }

                Then("stroke JSON 에는 zIndex 가 남지 않는다") {
                    command.stroke shouldBe mapOf("points" to listOf(listOf(1.0, 2.0)))
                }
            }
        }
    })
