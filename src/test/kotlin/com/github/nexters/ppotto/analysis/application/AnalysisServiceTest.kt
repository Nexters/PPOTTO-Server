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
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.jooq.DSLContext
import org.springframework.context.annotation.Import
import java.time.Instant
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

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
                            analysisService.createAnalysis(board.userId, board.id, photos)
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
                            analysisService.createAnalysis(board.userId, board.id, photos)
                        }
                    exception.errorCode.code shouldBe "ANALYSIS-001"
                }
            }
        }

        Given("이미 활성 분석이 있는 사용자가") {
            val board = boardRepository.save(userRepository.save().id)
            val photos =
                (0 until 90).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), "image/jpeg") }
            analysisService.createAnalysis(board.userId, board.id, photos)

            When("새로운 분석 생성을 요청하면") {
                Then("ConflictException(ANALYSIS-002)이 발생한다") {
                    val exception =
                        shouldThrow<ConflictException> {
                            analysisService.createAnalysis(board.userId, board.id, photos)
                        }
                    exception.errorCode.code shouldBe "ANALYSIS-002"
                }
            }
        }

        Given("동일 사용자가 동시에 분석 생성을 요청하면") {
            val board = boardRepository.save(userRepository.save().id)
            val photos =
                (0 until 90).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), "image/jpeg") }

            When("두 요청이 거의 동시에 들어오면") {
                val startLatch = CountDownLatch(1)
                val results = Collections.synchronizedList(mutableListOf<Result<AnalysisCreationResult>>())

                val threads =
                    (0 until 2).map {
                        thread {
                            startLatch.await()
                            results += runCatching { analysisService.createAnalysis(board.userId, board.id, photos) }
                        }
                    }
                startLatch.countDown()
                threads.forEach { it.join() }

                Then("정확히 하나만 성공하고, 나머지는 DataIntegrityViolationException이 ConflictException(ANALYSIS-002)으로 변환된다") {
                    results shouldHaveSize 2
                    results.count { it.isSuccess } shouldBe 1

                    val failure = results.single { it.isFailure }.exceptionOrNull()
                    failure.shouldBeInstanceOf<ConflictException>()
                    failure.errorCode.code shouldBe "ANALYSIS-002"
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

            val result = analysisService.createAnalysis(board.userId, board.id, photos)

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
                    photo.contentType.mimeType shouldBe photos[i].contentType

                    val expectedKey = photoObjectKeys.keyFor(result.analysisId, upload.photoId, photo.contentType)
                    upload.uploadUrl shouldBe "https://fake-signed-url/$expectedKey"
                }
            }
        }

        Given("존재하지 않는 boardId로") {
            When("분석 생성을 요청하면") {
                Then("NotFoundException(BOARD-002)이 발생한다") {
                    val photos =
                        (0 until 90)
                            .map { i ->
                                PhotoUploadItemRequest(
                                    Instant.now().plusSeconds(i.toLong()),
                                    "image/jpeg",
                                )
                            }
                    val exception =
                        shouldThrow<NotFoundException> {
                            analysisService.createAnalysis(userRepository.save().id, UUID.randomUUID(), photos)
                        }
                    exception.errorCode.code shouldBe "BOARD-002"
                }
            }
        }

        Given("다른 사용자의 Board로") {
            val ownerBoard = boardRepository.save(userRepository.save().id)
            val otherUserId = userRepository.save().id
            val photos =
                (0 until 90)
                    .map { i ->
                        PhotoUploadItemRequest(
                            Instant.now().plusSeconds(i.toLong()),
                            "image/jpeg",
                        )
                    }

            When("분석 생성을 요청하면") {
                Then("NotFoundException(BOARD-002)이 발생한다") {
                    val exception =
                        shouldThrow<NotFoundException> {
                            analysisService.createAnalysis(otherUserId, ownerBoard.id, photos)
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
            val created = analysisService.createAnalysis(board.userId, board.id, photos)
            val missingPhotoId = created.uploads[1].photoId
            val missingPhoto = photoRepository.findPendingByAnalysisId(created.analysisId).first { it.id == missingPhotoId }
            photoStorage.markMissing(photoObjectKeys.keyFor(created.analysisId, missingPhotoId, missingPhoto.contentType))

            When("업로드 완료를 통보하면") {
                val result = analysisService.startUpload(board.userId, created.analysisId)

                Then("업로드된 사진들은 COMPLETED로 바뀌고, 누락된 사진은 FAILED로 제외된다") {
                    result.uploadedCount shouldBe 89
                    result.failedCount shouldBe 1
                    result.failedPhotoIds shouldContainExactly listOf(missingPhotoId)

                    photoRepository.findPendingByAnalysisId(created.analysisId).shouldBeEmpty()
                    val failedPhoto = photoRepository.findAllByAnalysisId(created.analysisId).first { it.id == missingPhotoId }
                    failedPhoto.uploadStatus shouldBe UploadStatus.FAILED
                }

                Then("analysis의 status는 COMPLETED로 바뀌고 startedAt이 기록된다") {
                    val analysis = analysisRepository.findById(created.analysisId)
                    analysis.shouldNotBeNull()
                    analysis.status shouldBe AnalysisStatus.COMPLETED
                    analysis.startedAt.shouldNotBeNull()
                }
            }
        }

        Given("부분 업로드된 분석이 이미 시작된 뒤 누락됐던 사진이 이후 업로드된 상태에서") {
            val board = boardRepository.save(userRepository.save().id)
            val photos =
                (0 until 90).map { i ->
                    PhotoUploadItemRequest(
                        Instant.now().plusSeconds(i.toLong()),
                        if (i % 2 == 0) "image/jpeg" else "image/png",
                    )
                }
            val created = analysisService.createAnalysis(board.userId, board.id, photos)
            val missingPhotoId = created.uploads[1].photoId
            val missingPhoto = photoRepository.findPendingByAnalysisId(created.analysisId).first { it.id == missingPhotoId }
            photoStorage.markMissing(photoObjectKeys.keyFor(created.analysisId, missingPhotoId, missingPhoto.contentType))
            analysisService.startUpload(board.userId, created.analysisId)
            photoStorage.markUploaded(photoObjectKeys.keyFor(created.analysisId, missingPhotoId, missingPhoto.contentType))

            When("다시 업로드 완료를 통보하면") {
                Then("이미 시작된 분석이므로 ConflictException(ANALYSIS-003)이 발생한다") {
                    val exception = shouldThrow<ConflictException> { analysisService.startUpload(board.userId, created.analysisId) }
                    exception.errorCode.code shouldBe "ANALYSIS-003"
                }
            }
        }

        Given("모든 사진이 업로드되어 분석이 시작된 상태에서") {
            val board = boardRepository.save(userRepository.save().id)
            val photos =
                (0 until 90).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), "image/jpeg") }
            val created = analysisService.createAnalysis(board.userId, board.id, photos)

            When("다시 업로드 완료를 통보하면") {
                analysisService.startUpload(board.userId, created.analysisId)

                Then("이미 시작된 분석이므로 ConflictException(ANALYSIS-003)이 발생한다") {
                    val exception = shouldThrow<ConflictException> { analysisService.startUpload(board.userId, created.analysisId) }
                    exception.errorCode.code shouldBe "ANALYSIS-003"
                }
            }
        }

        Given("분석이 생성되고 사진이 0바이트로(빈 바디로) 업로드된 상태에서") {
            val board = boardRepository.save(userRepository.save().id)
            val photos =
                (0 until 90).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), "image/jpeg") }
            val created = analysisService.createAnalysis(board.userId, board.id, photos)
            val photo = photoRepository.findPendingByAnalysisId(created.analysisId).first()
            photoStorage.markUploaded(photoObjectKeys.keyFor(created.analysisId, photo.id, photo.contentType), size = 0)

            When("업로드 완료를 통보하면") {
                val result = analysisService.startUpload(board.userId, created.analysisId)

                Then("COMPLETED로 확정하지 않고 FAILED로 제외한다") {
                    result.uploadedCount shouldBe 89
                    result.failedCount shouldBe 1
                    result.failedPhotoIds shouldContainExactly listOf(photo.id)

                    photoRepository.findPendingByAnalysisId(created.analysisId).shouldBeEmpty()
                    val failedPhoto = photoRepository.findAllByAnalysisId(created.analysisId).first { it.id == photo.id }
                    failedPhoto.uploadStatus shouldBe UploadStatus.FAILED
                }
            }
        }

        Given("진행 중이 아닌(UPLOADING이 아닌) 분석에") {
            val board = boardRepository.save(userRepository.save().id)
            val photos =
                (0 until 90).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), "image/jpeg") }
            val created = analysisService.createAnalysis(board.userId, board.id, photos)
            dslContext
                .update(ANALYSIS)
                .set(ANALYSIS.STATUS, AnalysisStatus.ANALYZING.name)
                .where(ANALYSIS.ID.eq(created.analysisId))
                .execute()

            When("업로드 완료를 통보하면") {
                Then("ConflictException(ANALYSIS-003)이 발생한다") {
                    val exception = shouldThrow<ConflictException> { analysisService.startUpload(board.userId, created.analysisId) }
                    exception.errorCode.code shouldBe "ANALYSIS-003"
                }
            }
        }

        Given("다른 사용자의 분석이 진행 중이 아닌 상태일 때") {
            val board = boardRepository.save(userRepository.save().id)
            val photos =
                (0 until 90).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), "image/jpeg") }
            val created = analysisService.createAnalysis(board.userId, board.id, photos)
            val otherUserId = userRepository.save().id
            dslContext
                .update(ANALYSIS)
                .set(ANALYSIS.STATUS, AnalysisStatus.ANALYZING.name)
                .where(ANALYSIS.ID.eq(created.analysisId))
                .execute()

            When("업로드 완료를 통보하면") {
                Then("상태 검증보다 먼저 NotFoundException이 발생한다") {
                    shouldThrow<NotFoundException> {
                        analysisService.startUpload(otherUserId, created.analysisId)
                    }
                }
            }
        }

        Given("존재하지 않는 analysisId로") {
            When("업로드 완료를 통보하면") {
                Then("NotFoundException이 발생한다") {
                    shouldThrow<NotFoundException> {
                        analysisService.startUpload(userRepository.save().id, UUID.randomUUID())
                    }
                }
            }
        }

        Given("Board가 등록된 상태에서") {
            val board = boardRepository.save(userRepository.save().id)

            When("지원하지 않는 contentType으로 분석 생성을 요청하면") {
                Then("InvalidInputException이 발생한다") {
                    shouldThrow<InvalidInputException> {
                        analysisService.createAnalysis(
                            board.userId,
                            board.id,
                            listOf(PhotoUploadItemRequest(Instant.now(), "image/gif")),
                        )
                    }
                }
            }
        }

        Given("기존 분석이 COMPLETED 상태인 사용자가") {
            val board = boardRepository.save(userRepository.save().id)
            val photos = (0 until 90).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), "image/jpeg") }
            val created = analysisService.createAnalysis(board.userId, board.id, photos)
            dslContext
                .update(ANALYSIS)
                .set(ANALYSIS.STATUS, AnalysisStatus.COMPLETED.name)
                .where(ANALYSIS.ID.eq(created.analysisId))
                .execute()

            When("새로운 분석 생성을 요청하면") {
                Then("성공한다") {
                    val result = analysisService.createAnalysis(board.userId, board.id, photos)
                    result.analysisId.shouldNotBeNull()
                    result.uploads shouldHaveSize 90
                }
            }
        }

        Given("기존 분석이 FAILED 상태인 사용자가") {
            val board = boardRepository.save(userRepository.save().id)
            val photos = (0 until 90).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), "image/jpeg") }
            val created = analysisService.createAnalysis(board.userId, board.id, photos)
            dslContext
                .update(ANALYSIS)
                .set(ANALYSIS.STATUS, AnalysisStatus.FAILED.name)
                .where(ANALYSIS.ID.eq(created.analysisId))
                .execute()

            When("새로운 분석 생성을 요청하면") {
                Then("성공한다") {
                    val result = analysisService.createAnalysis(board.userId, board.id, photos)
                    result.analysisId.shouldNotBeNull()
                    result.uploads shouldHaveSize 90
                }
            }
        }
    })
