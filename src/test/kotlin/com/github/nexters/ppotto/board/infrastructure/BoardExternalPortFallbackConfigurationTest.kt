package com.github.nexters.ppotto.board.infrastructure

import com.github.nexters.ppotto.board.application.port.BoardStickerLayoutCommand
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

class BoardExternalPortFallbackConfigurationTest :
    BehaviorSpec({
        val configuration = BoardExternalPortFallbackConfiguration()

        Given("스티커 조회 adapter가 없는 standalone 구성에서") {
            val queryPort = configuration.boardStickerQueryPort()

            When("빈 보드의 스티커를 조회해도") {
                Then("연동 누락 오류를 던진다") {
                    val exception =
                        shouldThrow<IllegalStateException> {
                            queryPort.getByBoardId(UUID.randomUUID())
                        }
                    exception.message shouldBe "StickerQueryService 연동이 필요합니다."
                }
            }
        }

        Given("스티커 command adapter가 없는 standalone 구성에서") {
            val commandPort = configuration.boardStickerCommandPort()
            val boardId = UUID.randomUUID()

            When("빈 스티커 소유권을 검증해도") {
                Then("연동 누락 오류를 던진다") {
                    val exception =
                        shouldThrow<IllegalStateException> {
                            commandPort.validateOwnedByBoard(boardId, emptySet())
                        }
                    exception.message shouldBe "StickerCommandService 연동이 필요합니다."
                }
            }

            When("빈 레이아웃을 저장해도") {
                Then("연동 누락 오류를 던진다") {
                    val exception =
                        shouldThrow<IllegalStateException> {
                            commandPort.updateLayouts(boardId, emptyList<BoardStickerLayoutCommand>())
                        }
                    exception.message shouldBe "StickerCommandService 연동이 필요합니다."
                }
            }

            When("보드 스티커를 삭제해도") {
                Then("연동 누락 오류를 던진다") {
                    val exception =
                        shouldThrow<IllegalStateException> {
                            commandPort.deleteAllByBoardId(boardId)
                        }
                    exception.message shouldBe "StickerCommandService 연동이 필요합니다."
                }
            }
        }
    })
