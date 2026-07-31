package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.infrastructure.DrawingRepository
import com.github.nexters.ppotto.board.support.newDrawing
import com.github.nexters.ppotto.board.support.uuidV7
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.sticker.infrastructure.StickerCommandRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRecapRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRepository
import com.github.nexters.ppotto.sticker.support.textStickerCreation
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class StickerCommandServiceTest(
    service: StickerCommandService,
    stickerRepository: StickerRepository,
    stickerCommandRepository: StickerCommandRepository,
    stickerRecapRepository: StickerRecapRepository,
    stickerAccessService: StickerAccessService,
    analysisRepository: AnalysisRepository,
    boardRepository: BoardRepository,
    drawingRepository: DrawingRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        Given("사용자 보드에 스티커가 등록된 상태에서") {
            val board = boardRepository.save(userRepository.save().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            val sticker = stickerRepository.save(analysis.id, board.id, textStickerCreation())

            When("제목을 변경하고 열람 처리하면") {
                val renamed = service.rename(board.userId, sticker.id, "새 제목")
                service.markViewed(board.userId, sticker.id)
                service.markViewed(board.userId, sticker.id)

                Then("제목과 열람 상태가 저장된다") {
                    renamed.title shouldBe "새 제목"
                    val found = stickerRepository.findById(sticker.id)
                    found?.title shouldBe "새 제목"
                    found?.viewedAt.shouldNotBeNull()
                }
            }

            When("보드 배치를 변경하면") {
                service.updateLayouts(
                    board.id,
                    listOf(
                        StickerLayoutCommand(
                            id = sticker.id,
                            title = "배치 제목",
                            posX = 11.0,
                            posY = 12.0,
                            scale = 0.7,
                            rotation = 3.0,
                            zIndex = 5,
                            badgeOffsetX = 4.0,
                            badgeOffsetY = 6.0,
                            badgeRotation = 8.0,
                        ),
                    ),
                )

                Then("도메인 규칙을 거쳐 모든 배치값이 저장된다") {
                    val found = stickerRepository.findById(sticker.id)
                    found?.title shouldBe "배치 제목"
                    found?.posX shouldBe 11.0
                    found?.zIndex shouldBe 5
                    found?.badgeRotation shouldBe 8.0
                }
            }

            When("다른 사용자가 제목을 변경하면") {
                val otherUser = userRepository.save()

                Then("스티커 없음 예외로 소유권을 숨긴다") {
                    shouldThrow<NotFoundException> {
                        service.rename(otherUser.id, sticker.id, "침범")
                    }
                }
            }

            When("스티커를 삭제하면") {
                val stickerDrawingId = uuidV7()
                val boardDrawingId = uuidV7()
                drawingRepository.upsertAll(
                    listOf(
                        newDrawing(boardId = board.id, stickerId = sticker.id, id = stickerDrawingId),
                        newDrawing(boardId = board.id, id = boardDrawingId),
                    ),
                )
                service.delete(board.userId, sticker.id)

                Then("활성 스티커에서 제외하고 연결 드로잉을 삭제한다") {
                    stickerRepository.findById(sticker.id).shouldBeNull()
                    drawingRepository.findByBoardId(board.id).map { it.id } shouldBe listOf(boardDrawingId)
                }
            }
        }

        Given("드로잉 삭제 port가 없는 상태에서") {
            val board = boardRepository.save(userRepository.save().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            val sticker = stickerRepository.save(analysis.id, board.id, textStickerCreation())
            val serviceWithoutPort =
                StickerCommandService(
                    stickerRepository,
                    stickerCommandRepository,
                    stickerRecapRepository,
                    stickerAccessService,
                    emptyList(),
                )

            When("스티커 삭제를 요청하면") {
                Then("삭제를 시작하지 않고 실패한다") {
                    shouldThrow<IllegalStateException> {
                        serviceWithoutPort.delete(board.userId, sticker.id)
                    }
                    stickerRepository.findById(sticker.id).shouldNotBeNull()
                }
            }
        }
    })
