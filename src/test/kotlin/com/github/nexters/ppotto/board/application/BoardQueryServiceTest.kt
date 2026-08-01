package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.domain.DrawingScope
import com.github.nexters.ppotto.board.domain.NewDrawing
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.infrastructure.DrawingRepository
import com.github.nexters.ppotto.board.support.BoardTestConfig
import com.github.nexters.ppotto.board.support.FakeBoardStickerPort
import com.github.nexters.ppotto.board.support.boardStickerItem
import com.github.nexters.ppotto.board.support.uuidV7
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.rawId
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.springframework.context.annotation.Import

@Import(BoardTestConfig::class)
class BoardQueryServiceTest(
    boardQueryService: BoardQueryService,
    boardRepository: BoardRepository,
    drawingRepository: DrawingRepository,
    userRepository: UserRepository,
    stickerPort: FakeBoardStickerPort,
) : IntegrationTest({
        Given("스티커와 드로잉이 있는 보드를 소유한 사용자가") {
            stickerPort.reset()
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.rawId, "여름 휴가")
            val sticker = boardStickerItem()
            val drawing =
                NewDrawing(
                    id = uuidV7(),
                    boardId = board.id,
                    stickerId = sticker.id,
                    scope = DrawingScope.STICKER,
                    stroke = mapOf("points" to listOf(listOf(10.5, 22.0))),
                    color = "#FFD400",
                    strokeWidth = 4.0,
                )
            stickerPort.stickersByBoardId[board.id] = listOf(sticker)
            drawingRepository.upsertAll(listOf(drawing))

            When("보드 상세를 조회하면") {
                val detail = boardQueryService.getDetail(board.id, user.rawId)

                Then("보드와 스티커와 드로잉을 함께 반환한다") {
                    detail.name shouldBe "여름 휴가"
                    detail.stickers.map { it.id } shouldContainExactly listOf(sticker.id)
                    detail.drawings.map { it.id } shouldContainExactly listOf(drawing.id)
                }
            }

            When("다른 사용자가 조회하면") {
                Then("BOARD-002 오류가 발생한다") {
                    val exception =
                        shouldThrow<NotFoundException> {
                            boardQueryService.getDetail(board.id, userRepository.saveTestUser().rawId)
                        }
                    exception.errorCode shouldBe BoardErrorCode.NOT_FOUND
                }
            }
        }
    })
