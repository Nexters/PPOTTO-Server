package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.port.BoardStickerLayoutCommand
import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.domain.Drawing
import com.github.nexters.ppotto.board.domain.DrawingScope
import com.github.nexters.ppotto.board.domain.NewDrawing
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.infrastructure.DrawingRepository
import com.github.nexters.ppotto.board.support.BoardTestConfig
import com.github.nexters.ppotto.board.support.FakeBoardStickerPort
import com.github.nexters.ppotto.board.support.boardStickerItem
import com.github.nexters.ppotto.board.support.uuidV7
import com.github.nexters.ppotto.global.error.CommonErrorCode
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.identifier.DrawingId
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.springframework.context.annotation.Import

@Import(BoardTestConfig::class)
class BoardLayoutServiceTest(
    boardLayoutService: BoardLayoutService,
    boardRepository: BoardRepository,
    drawingRepository: DrawingRepository,
    userRepository: UserRepository,
    stickerPort: FakeBoardStickerPort,
) : IntegrationTest({
        Given("보드 배경 드로잉을 저장한 사용자가") {
            stickerPort.reset()
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)
            val drawingId = DrawingId(uuidV7())
            val command =
                BoardLayoutUpdateCommand(
                    stickers = emptyList(),
                    createdDrawings =
                        listOf(
                            DrawingCreateCommand.Stroke(
                                id = drawingId,
                                scope = DrawingScope.BOARD,
                                zIndex = 0,
                                stickerId = null,
                                stroke = mapOf("points" to listOf(listOf(1.0, 2.0))),
                                color = "#FFFFFF",
                                strokeWidth = 2.0,
                            ),
                        ),
                    deletedDrawingIds = emptyList(),
                )
            boardLayoutService.update(board.id, user.id, command)

            When("같은 드로잉 아이디로 재시도하면") {
                boardLayoutService.update(
                    board.id,
                    user.id,
                    command.copy(
                        createdDrawings =
                            command.createdDrawings
                                .filterIsInstance<DrawingCreateCommand.Stroke>()
                                .map { it.copy(color = "#FFD400") },
                    ),
                )

                Then("한 건만 남고 최신 값으로 갱신된다") {
                    val drawings = drawingRepository.findByBoardId(board.id)
                    drawings shouldHaveSize 1
                    drawings.single().id shouldBe drawingId
                    drawings.single().color shouldBe "#FFD400"
                }
            }
        }

        Given("스티커 범위 드로잉과 스티커 배치를 함께 저장하는 사용자가") {
            stickerPort.reset()
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)
            val sticker = boardStickerItem()
            stickerPort.stickersByBoardId[board.id] = listOf(sticker)

            When("레이아웃 저장을 요청하면") {
                boardLayoutService.update(
                    board.id,
                    user.id,
                    BoardLayoutUpdateCommand(
                        stickers = listOf(stickerLayout(sticker.id)),
                        createdDrawings =
                            listOf(
                                DrawingCreateCommand.Stroke(
                                    id = DrawingId(uuidV7()),
                                    scope = DrawingScope.STICKER,
                                    zIndex = 0,
                                    stickerId = sticker.id,
                                    stroke = mapOf("points" to listOf(listOf(1.0, 2.0))),
                                    color = "#FFFFFF",
                                    strokeWidth = 2.0,
                                ),
                            ),
                        deletedDrawingIds = emptyList(),
                    ),
                )

                Then("스티커 소유권을 검증하고 두 변경을 모두 저장한다") {
                    stickerPort.validatedStickerIds.any { sticker.id in it } shouldBe true
                    stickerPort.updatedLayouts
                        .flatten()
                        .map { it.id } shouldBe listOf(sticker.id)
                    drawingRepository.findByBoardId(board.id) shouldHaveSize 1
                }
            }
        }

        Given("다른 보드의 드로잉 아이디가 삭제 목록에 섞인 경우") {
            stickerPort.reset()
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)
            val otherBoard = boardRepository.save(user.id)
            val foreignDrawing =
                NewDrawing.Stroke(
                    id = DrawingId(uuidV7()),
                    boardId = otherBoard.id,
                    stickerId = null,
                    scope = DrawingScope.BOARD,
                    zIndex = 0,
                    stroke = mapOf("points" to listOf(listOf(1.0, 2.0))),
                    color = "#FFFFFF",
                    strokeWidth = 2.0,
                )
            drawingRepository.upsertAll(listOf(foreignDrawing))

            When("레이아웃 저장을 요청하면") {
                Then("BOARD-001로 전체 거부하고 외부 변경을 시작하지 않는다") {
                    val exception =
                        shouldThrow<InvalidInputException> {
                            boardLayoutService.update(
                                board.id,
                                user.id,
                                BoardLayoutUpdateCommand(
                                    stickers = emptyList(),
                                    createdDrawings = emptyList(),
                                    deletedDrawingIds = listOf(foreignDrawing.id),
                                ),
                            )
                        }
                    exception.errorCode shouldBe BoardErrorCode.INVALID_LAYOUT
                    stickerPort.updatedLayouts.shouldBeEmpty()
                    drawingRepository.findByBoardId(otherBoard.id) shouldHaveSize 1
                }
            }
        }

        Given("소유하지 않은 스티커를 참조하는 드로잉이 있는 경우") {
            stickerPort.reset()
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)

            When("레이아웃 저장을 요청하면") {
                Then("BOARD-001로 거부하고 드로잉을 저장하지 않는다") {
                    val exception =
                        shouldThrow<InvalidInputException> {
                            boardLayoutService.update(
                                board.id,
                                user.id,
                                BoardLayoutUpdateCommand(
                                    stickers = emptyList(),
                                    createdDrawings =
                                        listOf(
                                            DrawingCreateCommand.Stroke(
                                                id = DrawingId(uuidV7()),
                                                scope = DrawingScope.STICKER,
                                                zIndex = 0,
                                                stickerId = StickerId(uuidV7()),
                                                stroke = mapOf("points" to listOf(listOf(1.0, 2.0))),
                                                color = "#FFFFFF",
                                                strokeWidth = 2.0,
                                            ),
                                        ),
                                    deletedDrawingIds = emptyList(),
                                ),
                            )
                        }
                    exception.errorCode shouldBe BoardErrorCode.INVALID_LAYOUT
                    drawingRepository.findByBoardId(board.id).shouldBeEmpty()
                }
            }
        }

        Given("보드에 텍스트를 얹으려는 사용자가") {
            stickerPort.reset()
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)

            When("32자 이하 문구로 저장을 요청하면") {
                boardLayoutService.update(board.id, user.id, textLayoutCommand("여름 휴가"))

                Then("텍스트가 보드에 저장된다") {
                    val text = drawingRepository.findByBoardId(board.id).single()
                    text.shouldBeInstanceOf<Drawing.Text>().content shouldBe "여름 휴가"
                }
            }

            When("32자를 넘는 문구로 저장을 요청하면") {
                val exception =
                    shouldThrow<InvalidInputException> {
                        boardLayoutService.update(board.id, user.id, textLayoutCommand("가".repeat(33)))
                    }

                Then("COMMON-001로 거부한다") {
                    exception.errorCode shouldBe CommonErrorCode.INVALID_INPUT
                }

                Then("텍스트는 저장되지 않는다") {
                    drawingRepository.findByBoardId(board.id).shouldBeEmpty()
                }
            }
        }
    })

private fun textLayoutCommand(content: String) =
    BoardLayoutUpdateCommand(
        stickers = emptyList(),
        createdDrawings =
            listOf(
                DrawingCreateCommand.Text(
                    id = DrawingId(uuidV7()),
                    scope = DrawingScope.BOARD,
                    stickerId = null,
                    color = "#FFFFFF",
                    zIndex = 7,
                    content = content,
                    fontSize = 26.0,
                    posX = 80.0,
                    posY = 290.5,
                    maxWidth = 280.0,
                    rotation = 0.0,
                ),
            ),
        deletedDrawingIds = emptyList(),
    )

private fun stickerLayout(id: StickerId) =
    BoardStickerLayoutCommand(
        id = id,
        title = "새 제목",
        posX = 1.0,
        posY = 2.0,
        scale = 1.0,
        rotation = 0.0,
        zIndex = 1,
        badgeOffsetX = 0.0,
        badgeOffsetY = 0.0,
        badgeRotation = 0.0,
    )
