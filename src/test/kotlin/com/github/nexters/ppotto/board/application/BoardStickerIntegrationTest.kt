package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import com.github.nexters.ppotto.analysis.infrastructure.persistence.AnalysisRepository
import com.github.nexters.ppotto.board.application.model.BoardLayoutUpdateCommand
import com.github.nexters.ppotto.board.application.port.BoardAnalysisActivityPort
import com.github.nexters.ppotto.board.application.port.BoardStickerCommandPort
import com.github.nexters.ppotto.board.application.port.BoardStickerLayoutCommand
import com.github.nexters.ppotto.board.application.port.BoardStickerQueryPort
import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.domain.BoardStickerType
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.infrastructure.DrawingRepository
import com.github.nexters.ppotto.board.support.changeAnalysisStatus
import com.github.nexters.ppotto.board.support.newDrawing
import com.github.nexters.ppotto.global.error.ConflictException
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.sticker.application.StickerCommandService
import com.github.nexters.ppotto.sticker.application.port.StickerDrawingCommandPort
import com.github.nexters.ppotto.sticker.domain.RecapCommentCreation
import com.github.nexters.ppotto.sticker.infrastructure.StickerRecapRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRepository
import com.github.nexters.ppotto.sticker.support.textStickerCreation
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import org.springframework.context.ApplicationContext

class BoardStickerIntegrationTest(
    applicationContext: ApplicationContext,
    boardQueryService: BoardQueryService,
    boardLayoutService: BoardLayoutService,
    boardCommandService: BoardCommandService,
    stickerCommandService: StickerCommandService,
    stickerCommandPort: BoardStickerCommandPort,
    boardRepository: BoardRepository,
    drawingRepository: DrawingRepository,
    stickerRepository: StickerRepository,
    stickerRecapRepository: StickerRecapRepository,
    analysisRepository: AnalysisRepository,
    userRepository: UserRepository,
    dslContext: DSLContext,
) : IntegrationTest({
        Given("실제 보드와 스티커 연동 빈이 기동된 상태에서") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id, "연동 보드")
            val analysis = analysisRepository.save(user.id, board.id)
            val sticker = stickerRepository.save(analysis.id, board.id, textStickerCreation())
            val drawing = newDrawing(boardId = board.id, stickerId = sticker.id)
            drawingRepository.upsertAll(listOf(drawing))

            When("보드 상세를 조회하면") {
                val detail = boardQueryService.getDetail(board.id, user.id)

                Then("순환 참조 없이 정확히 하나의 production port로 스티커와 드로잉을 조합한다") {
                    applicationContext.getBeansOfType(BoardAnalysisActivityPort::class.java) shouldHaveSize 1
                    applicationContext.getBeansOfType(BoardStickerQueryPort::class.java) shouldHaveSize 1
                    applicationContext.getBeansOfType(BoardStickerCommandPort::class.java) shouldHaveSize 1
                    applicationContext.getBeansOfType(StickerDrawingCommandPort::class.java) shouldHaveSize 1
                    detail.id shouldBe board.id
                    detail.name shouldBe "연동 보드"
                    detail.drawings.map { it.id } shouldContainExactly listOf(drawing.id)
                    detail.stickers.single().let {
                        it.id shouldBe sticker.id
                        it.title shouldBe sticker.title
                        it.isNew shouldBe true
                        it.type shouldBe BoardStickerType.TEXT
                        it.imageUrl.shouldBeNull()
                        it.textContent shouldBe sticker.textContent
                        it.posX shouldBe sticker.posX
                        it.posY shouldBe sticker.posY
                        it.scale shouldBe sticker.scale
                        it.rotation shouldBe sticker.rotation
                        it.zIndex shouldBe sticker.zIndex
                        it.badgeOffsetX shouldBe sticker.badgeOffsetX
                        it.badgeOffsetY shouldBe sticker.badgeOffsetY
                        it.badgeRotation shouldBe sticker.badgeRotation
                    }
                }
            }
        }

        Given("실제 스티커가 있는 보드에서") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)
            val analysis = analysisRepository.save(user.id, board.id)
            val sticker = stickerRepository.save(analysis.id, board.id, textStickerCreation())
            val drawing = newDrawing(boardId = board.id, stickerId = sticker.id)

            When("스티커 배치와 드로잉을 함께 저장하면") {
                boardLayoutService.update(
                    board.id,
                    user.id,
                    BoardLayoutUpdateCommand(
                        stickers = listOf(updatedLayout(sticker.id)),
                        createdDrawings = listOf(drawing),
                        deletedDrawingIds = emptyList(),
                    ),
                )

                Then("스티커 command mapping과 드로잉 저장을 같은 트랜잭션에서 반영한다") {
                    stickerRepository
                        .findById(sticker.id)
                        .shouldNotBeNull()
                        .apply {
                            title shouldBe "변경 제목"
                            posX shouldBe 11.0
                            posY shouldBe 12.0
                            scale shouldBe 0.7
                            rotation shouldBe 3.0
                            zIndex shouldBe 5
                            badgeOffsetX shouldBe 4.0
                            badgeOffsetY shouldBe 6.0
                            badgeRotation shouldBe 8.0
                        }
                    drawingRepository.findByBoardId(board.id).map { it.id } shouldContainExactly listOf(drawing.id)
                }
            }
        }

        Given("다른 보드의 스티커를 소유한 사용자가") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)
            val otherBoard = boardRepository.save(user.id)
            val otherAnalysis = analysisRepository.save(user.id, otherBoard.id)
            val otherSticker = stickerRepository.save(otherAnalysis.id, otherBoard.id, textStickerCreation())

            When("그 스티커 배치를 다른 보드의 레이아웃으로 저장하면") {
                Then("실제 스티커 연동 port가 소유권을 부정해 BOARD-001로 거부하고 제목을 바꾸지 않는다") {
                    val exception =
                        shouldThrow<InvalidInputException> {
                            boardLayoutService.update(
                                board.id,
                                user.id,
                                BoardLayoutUpdateCommand(
                                    stickers = listOf(updatedLayout(otherSticker.id)),
                                    createdDrawings = emptyList(),
                                    deletedDrawingIds = emptyList(),
                                ),
                            )
                        }
                    exception.errorCode shouldBe BoardErrorCode.INVALID_LAYOUT
                    stickerRepository
                        .findById(otherSticker.id)
                        .shouldNotBeNull()
                        .title shouldBe otherSticker.title
                }
            }

            When("보드 guard를 건너뛰고 실제 adapter에 직접 배치를 저장하면") {
                Then("STICKER-008로 거부하고 제목을 바꾸지 않는다") {
                    val exception =
                        shouldThrow<InvalidInputException> {
                            stickerCommandPort.updateLayouts(board.id, listOf(updatedLayout(otherSticker.id)))
                        }
                    exception.errorCode.code shouldBe "STICKER-008"
                    stickerRepository
                        .findById(otherSticker.id)
                        .shouldNotBeNull()
                        .title shouldBe otherSticker.title
                }
            }
        }

        Given("스티커와 보드 범위 드로잉이 함께 있는 상태에서") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)
            val analysis = analysisRepository.save(user.id, board.id)
            val sticker = stickerRepository.save(analysis.id, board.id, textStickerCreation())
            val stickerDrawing = newDrawing(boardId = board.id, stickerId = sticker.id)
            val boardDrawing = newDrawing(boardId = board.id)
            drawingRepository.upsertAll(listOf(stickerDrawing, boardDrawing))
            stickerRecapRepository.saveComments(
                sticker.id,
                listOf(RecapCommentCreation("리캡", null, null)),
            )

            When("스티커를 삭제하면") {
                stickerCommandService.delete(user.id, sticker.id)

                Then("해당 스티커의 드로잉과 리캡만 정리한다") {
                    stickerRepository.findById(sticker.id).shouldBeNull()
                    drawingRepository.findByBoardId(board.id).map { it.id } shouldContainExactly listOf(boardDrawing.id)
                    stickerRecapRepository.findComments(sticker.id).shouldBeEmpty()
                }
            }
        }

        Given("삭제 가능한 보드에 스티커와 리캡과 드로잉이 있는 상태에서") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)
            boardRepository.save(user.id)
            val analysis = analysisRepository.save(user.id, board.id)
            dslContext.changeAnalysisStatus(analysis.id, AnalysisStatus.COMPLETED)
            val sticker = stickerRepository.save(analysis.id, board.id, textStickerCreation())
            drawingRepository.upsertAll(
                listOf(newDrawing(boardId = board.id, stickerId = sticker.id), newDrawing(boardId = board.id)),
            )
            stickerRecapRepository.saveComments(
                sticker.id,
                listOf(RecapCommentCreation("리캡", null, null)),
            )

            When("보드를 삭제하면") {
                boardCommandService.delete(board.id, user.id)

                Then("보드와 스티커와 리캡과 모든 드로잉을 함께 정리한다") {
                    boardRepository.findOwnedById(board.id, user.id).shouldBeNull()
                    stickerRepository.findById(sticker.id).shouldBeNull()
                    stickerRecapRepository.findComments(sticker.id).shouldBeEmpty()
                    drawingRepository.findByBoardId(board.id).shouldBeEmpty()
                }
            }
        }

        Given("진행 중인 분석이 있는 삭제 가능한 보드에서") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)
            boardRepository.save(user.id)
            analysisRepository.save(user.id, board.id)

            When("보드를 삭제하면") {
                Then("실제 분석 연동 port가 BOARD-005로 거부한다") {
                    val exception =
                        shouldThrow<ConflictException> {
                            boardCommandService.delete(board.id, user.id)
                        }
                    exception.errorCode shouldBe BoardErrorCode.ACTIVE_ANALYSIS_EXISTS
                    boardRepository.findOwnedById(board.id, user.id)?.id shouldBe board.id
                }
            }
        }
    })

private fun updatedLayout(stickerId: StickerId) =
    BoardStickerLayoutCommand(
        id = stickerId,
        title = "변경 제목",
        posX = 11.0,
        posY = 12.0,
        scale = 0.7,
        rotation = 3.0,
        zIndex = 5,
        badgeOffsetX = 4.0,
        badgeOffsetY = 6.0,
        badgeRotation = 8.0,
    )
