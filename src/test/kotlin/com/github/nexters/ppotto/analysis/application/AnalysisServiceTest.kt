package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import com.github.nexters.ppotto.analysis.domain.UploadStatus
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.PhotoObjectKeys
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
import java.time.temporal.ChronoUnit
import java.util.UUID

@Import(AnalysisTestConfig::class)
class AnalysisServiceTest(
    private val analysisService: AnalysisService,
    private val analysisRepository: AnalysisRepository,
    private val photoRepository: PhotoRepository,
    private val photoStorage: FakePhotoStorage,
    private val dslContext: DSLContext,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        val photoObjectKeys = PhotoObjectKeys

        beforeSpec {
            photoStorage.clear()
        }

        Given("사진이 89장으로(하한 미만) 요청되면") {
            val board = boardRepository.save(userRepository.save().id)
            val photos =
                (0 until 89).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), "image/jpeg") }

            When("분석 생성을 요청하면") {
                Then("InvalidInputException(ANALYSIS-001)이 발생한다") {
                    val exception =
                        shouldThrow<InvalidInputException> {
                            analysisService.createAnalysis(board.id, photos)
                        }
                    exception.errorCode.code shouldBe "ANALYSIS-001"
                }
            }
        }

        Given("사진이 101장으로(상한 초과) 요청되면") {
            val board = boardRepository.save(userRepository.save().id)
            val photos =
                (0 until 101).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), "image/jpeg") }

            When("분석 생성을 요청하면") {
                Then("InvalidInputException(ANALYSIS-001)이 발생한다") {
                    val exception =
                        shouldThrow<InvalidInputException> {
                            analysisService.createAnalysis(board.id, photos)
                        }
                    exception.errorCode.code shouldBe "ANALYSIS-001"
                }
            }
        }

        Given("이미 활성 분석이 있는 사용자가") {
            val board = boardRepository.save(userRepository.save().id)
            val photos =
                (0 until 90).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), "image/jpeg") }
            analysisService.createAnalysis(board.id, photos)

            When("새로운 분석 생성을 요청하면") {
                Then("ConflictException(ANALYSIS-002)이 발생한다") {
                    val exception =
                        shouldThrow<ConflictException> {
                            analysisService.createAnalysis(board.id, photos)
                        }
                    exception.errorCode.code shouldBe "ANALYSIS-002"
                }
            }
        }

        Given("Board가 등록된 상태에서 여러 장의 사진으로 분석 생성을 요청하면") {
            val board = boardRepository.save(userRepository.save().id)
            val photos =
                (0 until 90).map { i ->
                    PhotoUploadItemRequest(
                        Instant.parse("2026-07-01T00:00:00Z").plusSeconds(i.toLong()),
                        if (i % 2 == 0) "image/jpeg" else "image/png",
                    )
                }

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
                val savedPhotos = photoRepository.findAllByAnalysisId(result.analysisId)
                savedPhotos shouldHaveSize photos.size
                savedPhotos.forEach { it.uploadStatus shouldBe UploadStatus.PENDING }

                result.uploads.forEachIndexed { i, upload ->
                    val photo = savedPhotos.first { it.id == upload.photoId }
                    photo.takenAt shouldBe photos[i].takenAt
                    photo.contentType.mimeType shouldBe
                        (photos[i].contentType ?: "image/jpeg")

                    val expectedKey = photoObjectKeys.keyFor(result.analysisId, upload.photoId, photo.contentType)
                    upload.uploadUrl shouldBe "https://fake-signed-url/$expectedKey"
                }
            }
        }

        Given("존재하지 않는 boardId로") {
            When("분석 생성을 요청하면") {
                Then("NotFoundException(BOARD-002)이 발생한다") {
                    val photos =
                        (0 until 90).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), null) }
                    val exception = shouldThrow<NotFoundException> {
                        analysisService.createAnalysis(UUID.randomUUID(), photos)
                    }
                    exception.errorCode.code shouldBe "BOARD-002"
                }
            }
        }

        Given("분석이 생성되고 사진 중 일부만 실제로 업로드된 상태에서") {
            val board = boardRepository.save(userRepository.save().id)
            val photos =
                (0 until 90).map { i ->
                    PhotoUploadItemRequest(
                        Instant.now().plusSeconds(i.toLong()),
                        if (i % 2 == 0) "image/jpeg" else "image/png",
                    )
                }
            val created = analysisService.createAnalysis(board.id, photos)
            val missingPhotoId = created.uploads[1].photoId
            val missingPhoto = photoRepository.findPendingByAnalysisId(created.analysisId).first { it.id == missingPhotoId }
            photoStorage.markMissing(photoObjectKeys.keyFor(created.analysisId, missingPhotoId, missingPhoto.contentType))

            When("업로드 완료를 통보하면") {
                val result = analysisService.startUpload(created.analysisId)

                Then("업로드된 사진들은 COMPLETED로 바뀌고, 누락된 사진은 PENDING을 유지한 채 failedPhotoIds로만 보고된다") {
                    result.uploadedCount shouldBe 89
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
                val uploadedAt = Instant.parse("2026-06-15T10:00:00Z").truncatedTo(ChronoUnit.MICROS)
                photoStorage.markUploaded(
                    photoObjectKeys.keyFor(created.analysisId, missingPhotoId, missingPhoto.contentType),
                    createdAt = uploadedAt,
                )
                val result = analysisService.startUpload(created.analysisId)

                Then("뒤늦게 업로드된 사진도 COMPLETED로 바뀌고, 응답은 analysis 전체의 최종 집계다") {
                    result.uploadedCount shouldBe 90
                    result.failedCount shouldBe 0
                    result.failedPhotoIds.shouldBeEmpty()
                    photoRepository.findPendingByAnalysisId(created.analysisId).shouldBeEmpty()
                }

                Then("uploadedAt은 확인 시각이 아닌 실제 업로드(GCS 객체 생성) 시각으로 저장된다") {
                    val completedPhoto = photoRepository.findAllByAnalysisId(created.analysisId).first { it.id == missingPhotoId }
                    completedPhoto.uploadedAt shouldBe uploadedAt
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

        Given("분석이 생성되고 사진이 0바이트로(빈 바디로) 업로드된 상태에서") {
            val board = boardRepository.save(userRepository.save().id)
            val photos =
                (0 until 90).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), "image/jpeg") }
            val created = analysisService.createAnalysis(board.id, photos)
            val photo = photoRepository.findPendingByAnalysisId(created.analysisId).first()
            photoStorage.markUploaded(photoObjectKeys.keyFor(created.analysisId, photo.id, photo.contentType), size = 0)

            When("업로드 완료를 통보하면") {
                val result = analysisService.startUpload(created.analysisId)

                Then("COMPLETED로 확정하지 않고 PENDING을 유지한 채 failedPhotoIds로 보고한다") {
                    result.uploadedCount shouldBe 89
                    result.failedCount shouldBe 1
                    result.failedPhotoIds shouldContainExactly listOf(photo.id)

                    val pending = photoRepository.findPendingByAnalysisId(created.analysisId)
                    pending shouldHaveSize 1
                    pending.first().id shouldBe photo.id
                    pending.first().uploadStatus shouldBe UploadStatus.PENDING
                }
            }
        }

        Given("진행 중이 아닌(UPLOADING이 아닌) 분석에") {
            val board = boardRepository.save(userRepository.save().id)
            val photos =
                (0 until 90).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), "image/jpeg") }
            val created = analysisService.createAnalysis(board.id, photos)
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

        Given("기존 분석이 COMPLETED 상태인 사용자가") {
            val board = boardRepository.save(userRepository.save().id)
            val photos = (0 until 90).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), "image/jpeg") }
            val created = analysisService.createAnalysis(board.id, photos)
            dslContext
                .update(ANALYSIS)
                .set(ANALYSIS.STATUS, AnalysisStatus.COMPLETED.name)
                .where(ANALYSIS.ID.eq(created.analysisId))
                .execute()

            When("새로운 분석 생성을 요청하면") {
                Then("성공한다") {
                    val result = analysisService.createAnalysis(board.id, photos)
                    result.analysisId.shouldNotBeNull()
                    result.uploads shouldHaveSize 90
                }
            }
        }

        Given("기존 분석이 FAILED 상태인 사용자가") {
            val board = boardRepository.save(userRepository.save().id)
            val photos = (0 until 90).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), "image/jpeg") }
            val created = analysisService.createAnalysis(board.id, photos)
            dslContext
                .update(ANALYSIS)
                .set(ANALYSIS.STATUS, AnalysisStatus.FAILED.name)
                .where(ANALYSIS.ID.eq(created.analysisId))
                .execute()

            When("새로운 분석 생성을 요청하면") {
                Then("성공한다") {
                    val result = analysisService.createAnalysis(board.id, photos)
                    result.analysisId.shouldNotBeNull()
                    result.uploads shouldHaveSize 90
                }
            }
        }
    })
