package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import com.github.nexters.ppotto.analysis.domain.PhotoObjectKeys
import com.github.nexters.ppotto.analysis.domain.UploadStatus
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.PhotoRepository
import com.github.nexters.ppotto.analysis.support.AnalysisTestConfig
import com.github.nexters.ppotto.analysis.support.FakePhotoStorage
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.error.ConflictException
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.jooq.tables.references.ANALYSIS
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import org.springframework.context.annotation.Import
import java.time.Instant
import java.util.UUID

@Import(AnalysisTestConfig::class)
class AnalysisServiceTest(
    private val analysisService: AnalysisService,
    private val analysisRepository: AnalysisRepository,
    private val photoRepository: PhotoRepository,
    private val photoObjectKeys: PhotoObjectKeys,
    private val photoStorage: FakePhotoStorage,
    private val dslContext: DSLContext,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        Given("Board가 등록된 상태에서 여러 장의 사진으로 분석 생성을 요청하면") {
            val board = boardRepository.save(userRepository.save().id)
            val photos =
                listOf(
                    PhotoUploadItemRequest(Instant.parse("2026-07-01T00:00:00Z"), "image/jpeg"),
                    PhotoUploadItemRequest(Instant.parse("2026-07-02T00:00:00Z"), null),
                )

            val result = analysisService.createAnalysis(board.id, photos)

            Then("UPLOADING 상태의 analysis가 생성된다") {
                val analysis = analysisRepository.findById(result.analysisId)
                analysis.shouldNotBeNull()
                analysis.status shouldBe AnalysisStatus.UPLOADING
                analysis.boardId shouldBe board.id
                analysis.userId shouldBe board.userId
            }

            Then("요청 순서와 동일하게 photo가 PENDING 상태로 생성되고 signed URL이 발급된다") {
                result.uploads shouldHaveSize photos.size
                val savedPhotos = photoRepository.findPendingByAnalysisId(result.analysisId)
                savedPhotos.map { it.id }.toSet() shouldBe
                    result.uploads
                        .map { it.photoId }
                        .toSet()
                savedPhotos.forEach { it.uploadStatus shouldBe UploadStatus.PENDING }

                result.uploads.forEach { upload ->
                    val expectedKey =
                        photoObjectKeys.keyFor(
                            result.analysisId,
                            upload.photoId,
                            savedPhotos.first { it.id == upload.photoId }.contentType,
                        )
                    upload.uploadUrl shouldBe "https://fake-signed-url/$expectedKey"
                }
            }
        }

        Given("존재하지 않는 boardId로") {
            When("분석 생성을 요청하면") {
                Then("NotFoundException이 발생한다") {
                    shouldThrow<NotFoundException> {
                        analysisService.createAnalysis(
                            UUID.randomUUID(),
                            listOf(PhotoUploadItemRequest(Instant.now(), null)),
                        )
                    }
                }
            }
        }

        Given("분석이 생성되고 사진 중 일부만 실제로 업로드된 상태에서") {
            val board = boardRepository.save(userRepository.save().id)
            val created =
                analysisService.createAnalysis(
                    board.id,
                    listOf(
                        PhotoUploadItemRequest(Instant.now(), "image/jpeg"),
                        PhotoUploadItemRequest(Instant.now(), "image/png"),
                    ),
                )
            val missingPhotoId = created.uploads[1].photoId
            val missingPhoto = photoRepository.findPendingByAnalysisId(created.analysisId).first { it.id == missingPhotoId }
            photoStorage.markMissing(photoObjectKeys.keyFor(created.analysisId, missingPhotoId, missingPhoto.contentType))

            When("업로드 완료를 통보하면") {
                val result = analysisService.startUpload(created.analysisId)

                Then("업로드된 사진은 COMPLETED로 바뀌고, 누락된 사진은 PENDING을 유지한 채 failedPhotoIds로만 보고된다") {
                    result.uploadedCount shouldBe 1
                    result.failedCount shouldBe 1
                    result.failedPhotoIds shouldContainExactly listOf(missingPhotoId)

                    val missing = photoRepository.findPendingByAnalysisId(created.analysisId)
                    missing shouldHaveSize 1
                    missing.first().id shouldBe missingPhotoId
                    missing.first().uploadStatus shouldBe UploadStatus.PENDING
                }

                Then("analysis의 status/startedAt은 그대로 UPLOADING/null이다") {
                    val analysis = analysisRepository.findById(created.analysisId)
                    analysis.shouldNotBeNull()
                    analysis.status shouldBe AnalysisStatus.UPLOADING
                    analysis.startedAt.shouldBeNull()
                }
            }

            When("누락됐던 사진이 이후 업로드를 마치고 다시 통보하면") {
                analysisService.startUpload(created.analysisId)
                photoStorage.markUploaded(photoObjectKeys.keyFor(created.analysisId, missingPhotoId, missingPhoto.contentType))
                val result = analysisService.startUpload(created.analysisId)

                Then("뒤늦게 업로드된 사진도 COMPLETED로 바뀌고, 응답은 이번 호출의 델타가 아닌 analysis 전체의 최종 집계다") {
                    result.uploadedCount shouldBe 2
                    result.failedCount shouldBe 0
                    result.failedPhotoIds.shouldBeEmpty()
                    photoRepository.findPendingByAnalysisId(created.analysisId).shouldBeEmpty()
                }
            }

            When("모든 사진이 COMPLETED로 확정된 뒤 다시 통보하면") {
                analysisService.startUpload(created.analysisId)
                photoStorage.markUploaded(photoObjectKeys.keyFor(created.analysisId, missingPhotoId, missingPhoto.contentType))
                analysisService.startUpload(created.analysisId)
                val result = analysisService.startUpload(created.analysisId)

                Then("이미 처리된 사진은 다시 건드리지 않는다") {
                    result.uploadedCount shouldBe 0
                    result.failedCount shouldBe 0
                    result.failedPhotoIds.shouldBeEmpty()
                }
            }
        }

        Given("진행 중이 아닌(UPLOADING이 아닌) 분석에") {
            val board = boardRepository.save(userRepository.save().id)
            val created =
                analysisService.createAnalysis(board.id, listOf(PhotoUploadItemRequest(Instant.now(), "image/jpeg")))
            dslContext
                .update(ANALYSIS)
                .set(ANALYSIS.STATUS, AnalysisStatus.ANALYZING.name)
                .where(ANALYSIS.ID.eq(created.analysisId))
                .execute()

            When("업로드 완료를 통보하면") {
                Then("ConflictException(ANALYSIS-003)이 발생한다") {
                    val exception = shouldThrow<ConflictException> { analysisService.startUpload(created.analysisId) }
                    exception.errorCode.code shouldBe "ANALYSIS-003"
                }
            }
        }

        Given("존재하지 않는 analysisId로") {
            When("업로드 완료를 통보하면") {
                Then("NotFoundException이 발생한다") {
                    shouldThrow<NotFoundException> {
                        analysisService.startUpload(UUID.randomUUID())
                    }
                }
            }
        }

        Given("Board가 등록된 상태에서") {
            val board = boardRepository.save(userRepository.save().id)

            When("지원하지 않는 contentType으로 분석 생성을 요청하면") {
                Then("InvalidInputException이 발생한다") {
                    shouldThrow<InvalidInputException> {
                        analysisService.createAnalysis(board.id, listOf(PhotoUploadItemRequest(Instant.now(), "image/gif")))
                    }
                }
            }
        }
    })
