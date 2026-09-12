package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.model.BoardLayoutUpdateCommand
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
import com.github.nexters.ppotto.board.support.newDrawing
import com.github.nexters.ppotto.board.support.newTextDrawing
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
import io.kotest.matchers.collections.shouldContainExactly
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
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)
            val drawingId = DrawingId(uuidV7())
            val command =
                BoardLayoutUpdateCommand(
                    stickers = emptyList(),
                    createdDrawings = listOf(newDrawing(boardId = board.id, id = drawingId)),
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
                                .filterIsInstance<NewDrawing.Stroke>()
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
                        createdDrawings = listOf(newDrawing(boardId = board.id, stickerId = sticker.id)),
                        deletedDrawingIds = emptyList(),
                    ),
                )

                Then("스티커에 붙은 드로잉을 저장한다") {
                    val drawing = drawingRepository.findByBoardId(board.id).single()
                    drawing.scope shouldBe DrawingScope.STICKER
                    drawing.stickerId shouldBe sticker.id
                }
            }
        }

        Given("다른 보드의 드로잉 아이디가 삭제 목록에 섞인 경우") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)
            val otherBoard = boardRepository.save(user.id)
            val foreignDrawing = newDrawing(boardId = otherBoard.id)
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

        Given("다른 보드에 이미 있는 드로잉 아이디를 생성 목록에 넣은 경우") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)
            val otherBoard = boardRepository.save(user.id)
            val foreignDrawing = newDrawing(boardId = otherBoard.id, color = "#FFFFFF")
            drawingRepository.upsertAll(listOf(foreignDrawing))

            When("같은 아이디로 이 보드에 새 드로잉을 만들려 하면") {
                Then("BOARD-001로 거부하고 다른 보드의 드로잉을 가져오지 않는다") {
                    val exception =
                        shouldThrow<InvalidInputException> {
                            boardLayoutService.update(
                                board.id,
                                user.id,
                                BoardLayoutUpdateCommand(
                                    stickers = emptyList(),
                                    createdDrawings =
                                        listOf(newDrawing(boardId = board.id, id = foreignDrawing.id, color = "#FFD400")),
                                    deletedDrawingIds = emptyList(),
                                ),
                            )
                        }
                    exception.errorCode shouldBe BoardErrorCode.INVALID_LAYOUT
                    stickerPort.updatedLayouts.shouldBeEmpty()
                    drawingRepository.findByBoardId(board.id).shouldBeEmpty()
                    drawingRepository
                        .findByBoardId(otherBoard.id)
                        .single()
                        .color shouldBe "#FFFFFF"
                }
            }
        }

        Given("소유하지 않은 스티커를 참조하는 드로잉이 있는 경우") {
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
                                        listOf(newDrawing(boardId = board.id, stickerId = StickerId(uuidV7()))),
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
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)

            When("32자 이하 문구로 저장을 요청하면") {
                boardLayoutService.update(
                    board.id,
                    user.id,
                    BoardLayoutUpdateCommand(
                        stickers = emptyList(),
                        createdDrawings = listOf(newTextDrawing(boardId = board.id, content = "여름 휴가")),
                        deletedDrawingIds = emptyList(),
                    ),
                )

                Then("텍스트가 보드에 저장된다") {
                    val text = drawingRepository.findByBoardId(board.id).single()
                    text.shouldBeInstanceOf<Drawing.Text>().content shouldBe "여름 휴가"
                }
            }
        }

        Given("이 보드의 활성 드로잉 두 개가 있는 사용자가") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)
            val deleted = newDrawing(boardId = board.id)
            val kept = newDrawing(boardId = board.id)
            drawingRepository.upsertAll(listOf(deleted, kept))

            When("한 개만 삭제 목록에 넣어 저장하면") {
                boardLayoutService.update(
                    board.id,
                    user.id,
                    BoardLayoutUpdateCommand(
                        stickers = emptyList(),
                        createdDrawings = emptyList(),
                        deletedDrawingIds = listOf(deleted.id),
                    ),
                )

                Then("삭제 대상만 숨기고 나머지는 남긴다") {
                    drawingRepository.findByBoardId(board.id).map { it.id } shouldContainExactly listOf(kept.id)
                }
            }

            When("같은 아이디를 두 번 삭제 목록에 넣으면") {
                Then("COMMON-001로 거부하고 아무것도 지우지 않는다") {
                    val exception =
                        shouldThrow<InvalidInputException> {
                            boardLayoutService.update(
                                board.id,
                                user.id,
                                BoardLayoutUpdateCommand(
                                    stickers = emptyList(),
                                    createdDrawings = emptyList(),
                                    deletedDrawingIds = listOf(deleted.id, deleted.id),
                                ),
                            )
                        }
                    exception.errorCode shouldBe CommonErrorCode.INVALID_INPUT
                    drawingRepository.findByBoardId(board.id) shouldHaveSize 2
                }
            }

            When("같은 아이디를 생성과 삭제에 함께 넣으면") {
                Then("COMMON-001로 거부하고 아무것도 지우지 않는다") {
                    val exception =
                        shouldThrow<InvalidInputException> {
                            boardLayoutService.update(
                                board.id,
                                user.id,
                                BoardLayoutUpdateCommand(
                                    stickers = emptyList(),
                                    createdDrawings = listOf(newDrawing(boardId = board.id, id = deleted.id)),
                                    deletedDrawingIds = listOf(deleted.id),
                                ),
                            )
                        }
                    exception.errorCode shouldBe CommonErrorCode.INVALID_INPUT
                    drawingRepository.findByBoardId(board.id) shouldHaveSize 2
                }
            }
        }

        Given("빈 보드와 스티커 하나를 가진 사용자가") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)
            val sticker = boardStickerItem()
            stickerPort.stickersByBoardId[board.id] = listOf(sticker)

            fun rejectedCommand(build: () -> Any?): InvalidInputException = shouldThrow(build)

            When("같은 드로잉 아이디를 생성 목록에 두 번 넣으면") {
                val drawingId = DrawingId(uuidV7())
                val exception =
                    rejectedCommand {
                        BoardLayoutUpdateCommand(
                            stickers = emptyList(),
                            createdDrawings =
                                listOf(
                                    newDrawing(boardId = board.id, id = drawingId),
                                    newDrawing(boardId = board.id, id = drawingId, color = "#FFD400"),
                                ),
                            deletedDrawingIds = emptyList(),
                        )
                    }

                Then("COMMON-001로 거부하고 드로잉을 저장하지 않는다") {
                    exception.errorCode shouldBe CommonErrorCode.INVALID_INPUT
                    drawingRepository.findByBoardId(board.id).shouldBeEmpty()
                    stickerPort.updatedLayouts.shouldBeEmpty()
                }
            }

            When("같은 스티커 아이디를 배치 목록에 두 번 넣으면") {
                val exception =
                    rejectedCommand {
                        BoardLayoutUpdateCommand(
                            stickers = listOf(stickerLayout(sticker.id), stickerLayout(sticker.id)),
                            createdDrawings = emptyList(),
                            deletedDrawingIds = emptyList(),
                        )
                    }

                Then("COMMON-001로 거부하고 스티커 배치를 위임하지 않는다") {
                    exception.errorCode shouldBe CommonErrorCode.INVALID_INPUT
                    stickerPort.updatedLayouts.shouldBeEmpty()
                }
            }

            When("스티커 좌표가 유한하지 않으면") {
                val exception = rejectedCommand { stickerLayout(sticker.id).copy(posX = Double.NaN) }

                Then("COMMON-001로 거부하고 스티커 배치를 위임하지 않는다") {
                    exception.errorCode shouldBe CommonErrorCode.INVALID_INPUT
                    stickerPort.updatedLayouts.shouldBeEmpty()
                }
            }

            When("스티커 확대 비율이 0 이하이면") {
                val exception = rejectedCommand { stickerLayout(sticker.id).copy(scale = 0.0) }

                Then("COMMON-001로 거부하고 스티커 배치를 위임하지 않는다") {
                    exception.errorCode shouldBe CommonErrorCode.INVALID_INPUT
                    stickerPort.updatedLayouts.shouldBeEmpty()
                }
            }
        }
    })

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
