package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.analysis.infrastructure.persistence.AnalysisRepository
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.sticker.application.model.StickerLayoutCommand
import com.github.nexters.ppotto.sticker.application.port.StickerDrawingCommandPort
import com.github.nexters.ppotto.sticker.domain.Sticker
import com.github.nexters.ppotto.sticker.domain.StickerErrorCode
import com.github.nexters.ppotto.sticker.infrastructure.StickerCommandRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRecapRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRepository
import com.github.nexters.ppotto.sticker.support.StaleStickerRepository
import com.github.nexters.ppotto.sticker.support.stickerLayout
import com.github.nexters.ppotto.sticker.support.textStickerCreation
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

class StickerBoardSurfaceTest(
    service: StickerCommandService,
    stickerRepository: StickerRepository,
    stickerCommandRepository: StickerCommandRepository,
    stickerRecapRepository: StickerRecapRepository,
    stickerAccessService: StickerAccessService,
    analysisRepository: AnalysisRepository,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
    transactionTemplate: TransactionTemplate,
    eventPublisher: ApplicationEventPublisher,
    dslContext: DSLContext,
) : IntegrationTest({
        fun layoutOf(id: StickerId) = StickerLayoutCommand(id, stickerLayout())

        fun posXOf(id: StickerId) = stickerRepository.findById(id)?.posX

        fun serviceReading(staleStickers: List<Sticker>) =
            StickerCommandService(
                StaleStickerRepository(dslContext, staleStickers),
                stickerCommandRepository,
                stickerRecapRepository,
                stickerAccessService,
                listOf(StickerDrawingCommandPort { _, _ -> }),
                emptyList(),
                transactionTemplate,
                eventPublisher,
            )

        Given("한 보드에 스티커 두 개가 있는 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            val first = stickerRepository.save(analysis.id, board.id, textStickerCreation(title = "첫 스티커"))
            val second = stickerRepository.save(analysis.id, board.id, textStickerCreation(title = "둘째 스티커"))

            When("같은 스티커 id가 두 번 들어간 배치를 저장하면") {
                val exception =
                    shouldThrow<InvalidInputException> {
                        service.updateLayouts(board.id, listOf(layoutOf(first.id), layoutOf(first.id)))
                    }

                Then("STICKER-008 오류로 거부한다") {
                    exception.errorCode shouldBe StickerErrorCode.UNEDITABLE_STICKER
                }

                Then("배치를 하나도 저장하지 않는다") {
                    posXOf(first.id).shouldBeNull()
                }
            }

            When("다른 보드의 스티커 id가 섞인 배치를 저장하면") {
                val otherBoard = boardRepository.save(userRepository.saveTestUser().id)
                val otherAnalysis = analysisRepository.save(otherBoard.userId, otherBoard.id)
                val otherSticker =
                    stickerRepository.save(otherAnalysis.id, otherBoard.id, textStickerCreation(title = "남의 스티커"))
                val exception =
                    shouldThrow<InvalidInputException> {
                        service.updateLayouts(board.id, listOf(layoutOf(first.id), layoutOf(otherSticker.id)))
                    }

                Then("STICKER-008 오류로 거부한다") {
                    exception.errorCode shouldBe StickerErrorCode.UNEDITABLE_STICKER
                }

                Then("같은 배치에 실린 내 스티커도 저장하지 않는다") {
                    posXOf(first.id).shouldBeNull()
                }

                Then("다른 보드 스티커도 건드리지 않는다") {
                    posXOf(otherSticker.id).shouldBeNull()
                }
            }

            When("소프트 삭제된 스티커 id가 섞인 배치를 저장하면") {
                stickerCommandRepository.softDelete(second.id, Instant.now())
                val exception =
                    shouldThrow<InvalidInputException> {
                        service.updateLayouts(board.id, listOf(layoutOf(first.id), layoutOf(second.id)))
                    }

                Then("STICKER-008 오류로 거부한다") {
                    exception.errorCode shouldBe StickerErrorCode.UNEDITABLE_STICKER
                }

                Then("살아있는 스티커의 배치도 저장하지 않는다") {
                    posXOf(first.id).shouldBeNull()
                }
            }

            When("배치를 읽은 뒤 저장 직전에 스티커 하나가 삭제되면") {
                val stale = listOf(first.id, second.id).map { checkNotNull(stickerRepository.findById(it)) }
                stickerCommandRepository.softDelete(second.id, Instant.now())
                val exception =
                    shouldThrow<InvalidInputException> {
                        transactionTemplate.executeWithoutResult {
                            serviceReading(stale).updateLayouts(board.id, listOf(layoutOf(first.id), layoutOf(second.id)))
                        }
                    }

                Then("STICKER-008 오류로 거부한다") {
                    exception.errorCode shouldBe StickerErrorCode.UNEDITABLE_STICKER
                }

                Then("먼저 반영된 배치까지 롤백한다") {
                    posXOf(first.id).shouldBeNull()
                }
            }

            When("보드 삭제를 읽은 뒤 삭제 직전에 스티커 하나가 삭제되면") {
                val stale = listOf(first.id, second.id).map { checkNotNull(stickerRepository.findById(it)) }
                stickerCommandRepository.softDelete(second.id, Instant.now())
                val exception =
                    shouldThrow<InvalidInputException> {
                        transactionTemplate.executeWithoutResult { serviceReading(stale).deleteAllByBoardId(board.id) }
                    }

                Then("STICKER-007 오류로 거부한다") {
                    exception.errorCode shouldBe StickerErrorCode.UNDELETABLE_STICKER
                }

                Then("먼저 지운 스티커까지 롤백해 보드가 그대로 남는다") {
                    stickerRepository.findById(first.id).shouldNotBeNull()
                }
            }
        }
    })
