package com.github.nexters.ppotto.user.application

import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.analysis.infrastructure.persistence.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.persistence.PhotoCreate
import com.github.nexters.ppotto.analysis.infrastructure.persistence.PhotoRepository
import com.github.nexters.ppotto.analysis.support.FakePhotoStorage
import com.github.nexters.ppotto.board.domain.DrawingScope
import com.github.nexters.ppotto.board.domain.NewDrawing
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.infrastructure.DrawingRepository
import com.github.nexters.ppotto.board.support.uuidV7
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.DrawingId
import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.global.identifier.UserId
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
import com.github.nexters.ppotto.jooq.tables.references.USER_DEVICE_TOKENS
import com.github.nexters.ppotto.notification.domain.DevicePlatform
import com.github.nexters.ppotto.notification.infrastructure.DeviceTokenRepository
import com.github.nexters.ppotto.sticker.application.AnalysisResultSaveService
import com.github.nexters.ppotto.sticker.application.model.AnalysisStickerResult
import com.github.nexters.ppotto.sticker.application.model.SaveAnalysisResultCommand
import com.github.nexters.ppotto.sticker.domain.RecapCommentCreation
import com.github.nexters.ppotto.sticker.domain.StickerType
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.RecordingObjectStorageCleaner
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.terms.application.TermsService
import com.github.nexters.ppotto.user.application.port.WithdrawnUserAnalysisDeletionPort
import com.github.nexters.ppotto.user.application.port.WithdrawnUserBoardDeletionPort
import com.github.nexters.ppotto.user.application.port.WithdrawnUserStickerDeletionPort
import com.github.nexters.ppotto.user.application.port.WithdrawnUserTermAgreementDeletionPort
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import java.time.Instant
import java.util.UUID

private const val STICKER_IMAGE_KEY = "stickers/withdrawn-result.png"
private const val WITHDRAWN_DEVICE_ID = "device-withdrawn"
private val WITHDRAWN_AT = Instant.parse("2026-07-01T00:00:00Z")
private val DELETED_BEFORE = Instant.parse("2026-07-02T00:00:00Z")

private data class WithdrawnUserFixture(
    val userId: UserId,
    val boardId: BoardId,
    val drawingId: DrawingId,
    val analysisId: AnalysisId,
    val photoId: PhotoId,
    val stickerId: StickerId,
)

class WithdrawnUserDataDeletionIntegrationTest(
    cleanupService: WithdrawnUserCleanupService,
    analysisResultSaveService: AnalysisResultSaveService,
    termsService: TermsService,
    userRepository: UserRepository,
    boardRepository: BoardRepository,
    drawingRepository: DrawingRepository,
    analysisRepository: AnalysisRepository,
    photoRepository: PhotoRepository,
    deviceTokenRepository: DeviceTokenRepository,
    boardDeletionPort: WithdrawnUserBoardDeletionPort,
    stickerDeletionPort: WithdrawnUserStickerDeletionPort,
    analysisDeletionPort: WithdrawnUserAnalysisDeletionPort,
    termAgreementDeletionPort: WithdrawnUserTermAgreementDeletionPort,
    photoStorage: FakePhotoStorage,
    objectStorageCleaner: RecordingObjectStorageCleaner,
    dslContext: DSLContext,
) : IntegrationTest({
        fun saveAnalysisSticker(
            userId: UserId,
            boardId: BoardId,
            analysisId: AnalysisId,
            photoId: PhotoId,
        ): StickerId =
            analysisResultSaveService
                .save(
                    SaveAnalysisResultCommand(
                        userId = userId,
                        analysisId = analysisId,
                        boardId = boardId,
                        stickers =
                            listOf(
                                AnalysisStickerResult(
                                    type = StickerType.IMAGE,
                                    title = "분석 결과",
                                    summary = "웃기고 귀여우면 일단 주워요",
                                    sourcePhotoId = photoId,
                                    imageKey = STICKER_IMAGE_KEY,
                                    textContent = null,
                                    mainColor = "#FF6B6B",
                                    photoIds = listOf(photoId),
                                    comments = listOf(RecapCommentCreation("리캡 코멘트", null, null)),
                                ),
                            ),
                    ),
                ).stickerIds
                .single()

        fun agreeToNewRequiredTerm(userId: UserId) {
            dslContext
                .insertInto(TERMS, TERMS.CODE, TERMS.VERSION, TERMS.IS_REQUIRED, TERMS.EFFECTIVE_AT)
                .values("CLEANUP-${UUID.randomUUID()}", "1.0", true, Instant.now().minusSeconds(60))
                .execute()
            termsService.agree(
                userId,
                termsService
                    .findCurrentTerms(userId)
                    .filter { it.isRequired }
                    .map { it.id },
            )
            check(dslContext.fetchExists(TERM_AGREEMENTS, TERM_AGREEMENTS.USER_ID.eq(userId))) {
                "약관 동의 픽스처가 저장되지 않아 삭제 순서를 검증할 수 없습니다."
            }
        }

        fun withdrawUserWithAllDomainData(): WithdrawnUserFixture {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)
            val drawing =
                drawingRepository
                    .upsertAll(
                        listOf(
                            NewDrawing.Stroke(
                                id = DrawingId(uuidV7()),
                                boardId = board.id,
                                stickerId = null,
                                scope = DrawingScope.BOARD,
                                zIndex = 0,
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
                        listOf(PhotoCreate(PhotoContentType.JPEG, WITHDRAWN_AT)),
                    ).single()
            photoRepository.markCompletedBatch(mapOf(photo.id to Instant.now()))
            val stickerId = saveAnalysisSticker(user.id, board.id, analysis.id, photo.id)
            agreeToNewRequiredTerm(user.id)
            deviceTokenRepository.upsert(user.id, WITHDRAWN_DEVICE_ID, DevicePlatform.IOS, "fcm-withdrawn")
            userRepository.withdraw(user.withdraw(WITHDRAWN_AT))!!
            return WithdrawnUserFixture(
                userId = user.id,
                boardId = board.id,
                drawingId = drawing.id,
                analysisId = analysis.id,
                photoId = photo.id,
                stickerId = stickerId,
            )
        }

        Given("모든 도메인 데이터를 가진 탈퇴 사용자가 유예기간을 지났을 때") {
            val fixture = withdrawUserWithAllDomainData()

            When("탈퇴 사용자 정리 배치를 실행하면") {
                val result = cleanupService.cleanup(deletedBefore = DELETED_BEFORE, batchSize = 10)

                Then("GCS 오브젝트와 모든 도메인 행을 삭제한 뒤 사용자 행을 하드 삭제한다") {
                    result.deletedUserIds shouldContainExactly listOf(fixture.userId)
                    photoStorage.deletedAnalysisIds shouldContain fixture.analysisId
                    objectStorageCleaner.deletedObjectKeys shouldContain STICKER_IMAGE_KEY
                    dslContext.fetchExists(RECAP_COMMENTS, RECAP_COMMENTS.STICKER_ID.eq(fixture.stickerId)) shouldBe false
                    dslContext.fetchExists(STICKER_PHOTOS, STICKER_PHOTOS.STICKER_ID.eq(fixture.stickerId)) shouldBe false
                    dslContext.fetchExists(STICKERS, STICKERS.ID.eq(fixture.stickerId)) shouldBe false
                    dslContext.fetchExists(DRAWINGS, DRAWINGS.ID.eq(fixture.drawingId)) shouldBe false
                    dslContext.fetchExists(PHOTOS, PHOTOS.ID.eq(fixture.photoId)) shouldBe false
                    dslContext.fetchExists(ANALYSIS, ANALYSIS.ID.eq(fixture.analysisId)) shouldBe false
                    dslContext.fetchExists(BOARDS, BOARDS.ID.eq(fixture.boardId)) shouldBe false
                    dslContext.fetchExists(TERM_AGREEMENTS, TERM_AGREEMENTS.USER_ID.eq(fixture.userId)) shouldBe false
                    dslContext.fetchExists(USERS, USERS.ID.eq(fixture.userId)) shouldBe false
                }

                Then("외래키가 없는 디바이스 토큰 행은 이 배치가 지우지 않는다") {
                    dslContext.fetchExists(
                        USER_DEVICE_TOKENS,
                        USER_DEVICE_TOKENS.USER_ID.eq(fixture.userId),
                    ) shouldBe true
                }
            }
        }

        Given("보드 삭제 포트가 실패하는 탈퇴 사용자가 있을 때") {
            val fixture = withdrawUserWithAllDomainData()
            val failingCleanupService =
                WithdrawnUserCleanupService(
                    userRepository = userRepository,
                    boardDeletionPort =
                        object : WithdrawnUserBoardDeletionPort {
                            override fun findAllBoardIds(userId: UserId) = boardDeletionPort.findAllBoardIds(userId)

                            override fun deleteAllByUserId(userId: UserId): Unit = error("보드 삭제 실패")
                        },
                    stickerDeletionPort = stickerDeletionPort,
                    analysisDeletionPort = analysisDeletionPort,
                    termAgreementDeletionPort = termAgreementDeletionPort,
                )

            When("정리 배치가 세 번째 포트에서 실패하면") {
                shouldThrow<IllegalStateException> {
                    failingCleanupService.cleanup(deletedBefore = DELETED_BEFORE, batchSize = 10)
                }

                Then("앞선 두 포트의 삭제만 끝나고 보드, 약관 동의, 사용자 행은 남는다") {
                    dslContext.fetchExists(STICKERS, STICKERS.ID.eq(fixture.stickerId)) shouldBe false
                    dslContext.fetchExists(PHOTOS, PHOTOS.ID.eq(fixture.photoId)) shouldBe false
                    dslContext.fetchExists(ANALYSIS, ANALYSIS.ID.eq(fixture.analysisId)) shouldBe false
                    dslContext.fetchExists(BOARDS, BOARDS.ID.eq(fixture.boardId)) shouldBe true
                    dslContext.fetchExists(DRAWINGS, DRAWINGS.ID.eq(fixture.drawingId)) shouldBe true
                    dslContext.fetchExists(TERM_AGREEMENTS, TERM_AGREEMENTS.USER_ID.eq(fixture.userId)) shouldBe true
                    dslContext.fetchExists(USERS, USERS.ID.eq(fixture.userId)) shouldBe true
                }

                And("정상 포트로 정리 배치를 다시 실행하면") {
                    val second = cleanupService.cleanup(deletedBefore = DELETED_BEFORE, batchSize = 10)

                    Then("이미 지운 데이터에 걸리지 않고 남은 데이터까지 지워 완전 삭제로 수렴한다") {
                        second.deletedUserIds shouldContainExactly listOf(fixture.userId)
                        dslContext.fetchExists(DRAWINGS, DRAWINGS.ID.eq(fixture.drawingId)) shouldBe false
                        dslContext.fetchExists(BOARDS, BOARDS.ID.eq(fixture.boardId)) shouldBe false
                        dslContext.fetchExists(TERM_AGREEMENTS, TERM_AGREEMENTS.USER_ID.eq(fixture.userId)) shouldBe false
                        dslContext.fetchExists(USERS, USERS.ID.eq(fixture.userId)) shouldBe false
                    }
                }
            }
        }

        Given("연관 데이터가 없는 탈퇴 사용자가 있을 때") {
            val user = userRepository.saveTestUser()
            userRepository.withdraw(user.withdraw(WITHDRAWN_AT))!!

            When("정리 배치를 두 번 실행하면") {
                cleanupService.cleanup(deletedBefore = DELETED_BEFORE, batchSize = 10)
                val second = cleanupService.cleanup(deletedBefore = DELETED_BEFORE, batchSize = 10)

                Then("두 번째 실행은 대상이 없어 멱등하게 끝난다") {
                    second.deletedUserIds.contains(user.id) shouldBe false
                    dslContext.fetchExists(USERS, USERS.ID.eq(user.id)) shouldBe false
                }
            }
        }
    })
