package com.github.nexters.ppotto.user.infrastructure.integration

import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.PhotoCreate
import com.github.nexters.ppotto.analysis.infrastructure.PhotoObjectKeys
import com.github.nexters.ppotto.analysis.infrastructure.PhotoRepository
import com.github.nexters.ppotto.board.domain.DrawingScope
import com.github.nexters.ppotto.board.domain.NewDrawing
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.infrastructure.DrawingRepository
import com.github.nexters.ppotto.board.support.uuidV7
import com.github.nexters.ppotto.jooq.tables.references.ANALYSIS
import com.github.nexters.ppotto.jooq.tables.references.BOARDS
import com.github.nexters.ppotto.jooq.tables.references.DRAWINGS
import com.github.nexters.ppotto.jooq.tables.references.PHOTOS
import com.github.nexters.ppotto.jooq.tables.references.RECAP_COMMENTS
import com.github.nexters.ppotto.jooq.tables.references.STICKERS
import com.github.nexters.ppotto.jooq.tables.references.STICKER_PHOTOS
import com.github.nexters.ppotto.jooq.tables.references.TERMS
import com.github.nexters.ppotto.jooq.tables.references.TERM_AGREEMENTS
import com.github.nexters.ppotto.jooq.tables.references.USERS
import com.github.nexters.ppotto.sticker.application.AnalysisResultSaveService
import com.github.nexters.ppotto.sticker.application.AnalysisStickerResult
import com.github.nexters.ppotto.sticker.application.SaveAnalysisResultCommand
import com.github.nexters.ppotto.sticker.domain.RecapCommentCreation
import com.github.nexters.ppotto.sticker.domain.StickerLayout
import com.github.nexters.ppotto.sticker.domain.StickerType
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.RecordingObjectStorageCleaner
import com.github.nexters.ppotto.terms.application.TermsService
import com.github.nexters.ppotto.user.application.WithdrawnUserCleanupService
import com.github.nexters.ppotto.user.application.port.WithdrawnUserDataDeletionPort
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.jooq.DSLContext
import org.springframework.context.ApplicationContext
import java.time.Instant
import java.util.UUID

private const val STICKER_IMAGE_KEY = "stickers/withdrawn-result.png"

class WithdrawnUserDataDeletionAdapterTest(
    applicationContext: ApplicationContext,
    cleanupService: WithdrawnUserCleanupService,
    analysisResultSaveService: AnalysisResultSaveService,
    termsService: TermsService,
    userRepository: UserRepository,
    boardRepository: BoardRepository,
    drawingRepository: DrawingRepository,
    analysisRepository: AnalysisRepository,
    photoRepository: PhotoRepository,
    objectStorageCleaner: RecordingObjectStorageCleaner,
    dslContext: DSLContext,
) : IntegrationTest({
        Given("운영 컨텍스트가 기동된 상태에서") {
            When("탈퇴 사용자 연관 데이터 삭제 port 빈을 조회하면") {
                Then("fail-closed fallback이 아닌 실제 어댑터가 정확히 하나 주입된다") {
                    applicationContext.getBeansOfType(WithdrawnUserDataDeletionPort::class.java).let {
                        it shouldHaveSize 1
                        it.values
                            .single()
                            .shouldBeInstanceOf<WithdrawnUserDataDeletionAdapter>()
                    }
                }
            }
        }

        Given("모든 도메인 데이터를 가진 탈퇴 사용자가 유예기간을 지났을 때") {
            objectStorageCleaner.clear()
            val user = userRepository.save()
            val board = boardRepository.save(user.id)
            val drawing =
                drawingRepository
                    .upsertAll(
                        listOf(
                            NewDrawing(
                                id = uuidV7(),
                                boardId = board.id,
                                stickerId = null,
                                scope = DrawingScope.BOARD,
                                stroke = mapOf("points" to listOf(1, 2)),
                                color = "#000000",
                                strokeWidth = 1.0,
                            ),
                        ),
                    ).single()
            val analysis = analysisRepository.save(user.id, board.id)
            val photo =
                photoRepository
                    .saveAll(
                        analysis.id,
                        board.id,
                        listOf(PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-01T00:00:00Z"))),
                    ).single()
            val stickerId =
                analysisResultSaveService
                    .save(
                        SaveAnalysisResultCommand(
                            userId = user.id,
                            analysisId = analysis.id,
                            boardId = board.id,
                            stickers =
                                listOf(
                                    AnalysisStickerResult(
                                        type = StickerType.IMAGE,
                                        title = "분석 결과",
                                        sourcePhotoId = photo.id,
                                        imageKey = STICKER_IMAGE_KEY,
                                        textContent = null,
                                        layout =
                                            StickerLayout(
                                                posX = 1.0,
                                                posY = 2.0,
                                                scale = 1.0,
                                                rotation = 0.0,
                                                zIndex = 0,
                                                badgeOffsetX = 0.0,
                                                badgeOffsetY = 0.0,
                                                badgeRotation = 0.0,
                                            ),
                                        photoIds = listOf(photo.id),
                                        comments = listOf(RecapCommentCreation("리캡 코멘트", false, null, null)),
                                    ),
                                ),
                        ),
                    ).stickerIds
                    .single()
            dslContext
                .insertInto(TERMS, TERMS.CODE, TERMS.VERSION, TERMS.IS_REQUIRED, TERMS.EFFECTIVE_AT)
                .values("CLEANUP-${UUID.randomUUID()}", "1.0", true, Instant.now().minusSeconds(60))
                .execute()
            termsService.agree(
                user.id,
                termsService
                    .findCurrentTerms(user.id)
                    .filter { it.isRequired }
                    .map { it.id },
            )
            dslContext.fetchExists(TERM_AGREEMENTS, TERM_AGREEMENTS.USER_ID.eq(user.id)) shouldBe true
            userRepository.withdraw(user.withdraw(Instant.parse("2026-07-01T00:00:00Z")))!!

            When("탈퇴 사용자 정리 배치를 실행하면") {
                val result =
                    cleanupService.cleanup(
                        deletedBefore = Instant.parse("2026-07-02T00:00:00Z"),
                        batchSize = 10,
                    )

                Then("GCS 오브젝트와 모든 도메인 행을 삭제한 뒤 사용자 행을 하드 삭제한다") {
                    result.deletedUserIds shouldContainExactly listOf(user.id)
                    objectStorageCleaner.deletedPrefixes shouldContain PhotoObjectKeys.prefixFor(analysis.id)
                    objectStorageCleaner.deletedObjectKeys shouldContain STICKER_IMAGE_KEY
                    dslContext.fetchExists(RECAP_COMMENTS, RECAP_COMMENTS.STICKER_ID.eq(stickerId)) shouldBe false
                    dslContext.fetchExists(STICKER_PHOTOS, STICKER_PHOTOS.STICKER_ID.eq(stickerId)) shouldBe false
                    dslContext.fetchExists(STICKERS, STICKERS.ID.eq(stickerId)) shouldBe false
                    dslContext.fetchExists(DRAWINGS, DRAWINGS.ID.eq(drawing.id)) shouldBe false
                    dslContext.fetchExists(PHOTOS, PHOTOS.ID.eq(photo.id)) shouldBe false
                    dslContext.fetchExists(ANALYSIS, ANALYSIS.ID.eq(analysis.id)) shouldBe false
                    dslContext.fetchExists(BOARDS, BOARDS.ID.eq(board.id)) shouldBe false
                    dslContext.fetchExists(TERM_AGREEMENTS, TERM_AGREEMENTS.USER_ID.eq(user.id)) shouldBe false
                    dslContext.fetchExists(USERS, USERS.ID.eq(user.id)) shouldBe false
                }
            }
        }

        Given("연관 데이터가 없는 탈퇴 사용자가 있을 때") {
            val user = userRepository.save()
            userRepository.withdraw(user.withdraw(Instant.parse("2026-07-01T00:00:00Z")))!!

            When("정리 배치를 두 번 실행하면") {
                cleanupService.cleanup(deletedBefore = Instant.parse("2026-07-02T00:00:00Z"), batchSize = 10)
                val second = cleanupService.cleanup(deletedBefore = Instant.parse("2026-07-02T00:00:00Z"), batchSize = 10)

                Then("두 번째 실행은 대상이 없어 멱등하게 끝난다") {
                    second.deletedUserIds.contains(user.id) shouldBe false
                    dslContext.fetchExists(USERS, USERS.ID.eq(user.id)) shouldBe false
                }
            }
        }
    })
