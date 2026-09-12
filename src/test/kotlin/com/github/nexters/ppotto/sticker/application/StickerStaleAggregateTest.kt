package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.analysis.infrastructure.persistence.AnalysisRepository
import com.github.nexters.ppotto.board.application.BoardAccessService
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.jooq.tables.references.STICKERS
import com.github.nexters.ppotto.sticker.application.port.StickerDrawingCommandPort
import com.github.nexters.ppotto.sticker.domain.RecapCommentCreation
import com.github.nexters.ppotto.sticker.domain.StickerErrorCode
import com.github.nexters.ppotto.sticker.infrastructure.StickerCommandRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRecapRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRepository
import com.github.nexters.ppotto.sticker.support.StaleStickerRepository
import com.github.nexters.ppotto.sticker.support.textStickerCreation
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

class StickerStaleAggregateTest(
    stickerRepository: StickerRepository,
    stickerCommandRepository: StickerCommandRepository,
    stickerRecapRepository: StickerRecapRepository,
    boardAccessService: BoardAccessService,
    analysisRepository: AnalysisRepository,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
    transactionTemplate: TransactionTemplate,
    eventPublisher: ApplicationEventPublisher,
    dslContext: DSLContext,
) : IntegrationTest({
        Given("소유권 확인 직후 다른 트랜잭션이 스티커를 삭제한 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            val sticker = stickerRepository.save(analysis.id, board.id, textStickerCreation())
            stickerRecapRepository.saveComments(sticker.id, listOf(RecapCommentCreation("키워드", null, null)))
            val stale = checkNotNull(stickerRepository.findById(sticker.id))
            stickerCommandRepository.softDelete(sticker.id, Instant.now())

            val staleRepository = StaleStickerRepository(dslContext, listOf(stale))
            val service =
                StickerCommandService(
                    staleRepository,
                    stickerCommandRepository,
                    stickerRecapRepository,
                    StickerAccessService(staleRepository, boardAccessService),
                    listOf(StickerDrawingCommandPort { _, _ -> }),
                    emptyList(),
                    transactionTemplate,
                    eventPublisher,
                )

            fun deletedRow() =
                dslContext
                    .selectFrom(STICKERS)
                    .where(STICKERS.ID.eq(sticker.id))
                    .fetchSingle()

            When("제목을 변경하면") {
                val exception = shouldThrow<NotFoundException> { service.rename(board.userId, sticker.id, "새 제목") }

                Then("STICKER-001 오류로 실패한다") {
                    exception.errorCode shouldBe StickerErrorCode.STICKER_NOT_FOUND
                }

                Then("삭제된 행의 제목은 그대로다") {
                    deletedRow().title shouldBe "원래 제목"
                }
            }

            When("열람 처리하면") {
                val exception = shouldThrow<NotFoundException> { service.markViewed(board.userId, sticker.id) }

                Then("STICKER-001 오류로 실패한다") {
                    exception.errorCode shouldBe StickerErrorCode.STICKER_NOT_FOUND
                }

                Then("삭제된 행의 열람 시각은 비어 있다") {
                    deletedRow().viewedAt.shouldBeNull()
                }
            }

            When("삭제하면") {
                val exception = shouldThrow<NotFoundException> { service.delete(board.userId, sticker.id) }

                Then("STICKER-001 오류로 실패한다") {
                    exception.errorCode shouldBe StickerErrorCode.STICKER_NOT_FOUND
                }

                Then("리캡 자식 삭제까지 가지 않는다") {
                    stickerRecapRepository.findComments(sticker.id).size shouldBe 1
                }
            }
        }
    })
