package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.analysis.domain.RecapContent
import com.github.nexters.ppotto.analysis.domain.ThemeClassification
import com.github.nexters.ppotto.analysis.domain.ThemeComment
import com.github.nexters.ppotto.analysis.domain.UploadStatus
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.PhotoObjectKeys
import com.github.nexters.ppotto.analysis.infrastructure.PhotoRepository
import com.github.nexters.ppotto.analysis.support.AnalysisTestConfig
import com.github.nexters.ppotto.analysis.support.FakeGeminiClassifier
import com.github.nexters.ppotto.analysis.support.FakePhotoStorage
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.error.ConflictException
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.jooq.tables.references.ANALYSIS
import com.github.nexters.ppotto.sticker.domain.StickerType
import com.github.nexters.ppotto.sticker.infrastructure.StickerRecapRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRepository
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
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
@Suppress("LargeClass")
class AnalysisServiceTest(
    private val analysisService: AnalysisService,
    private val analysisRepository: AnalysisRepository,
    private val photoRepository: PhotoRepository,
    private val photoStorage: FakePhotoStorage,
    private val geminiClassifier: FakeGeminiClassifier,
    private val dslContext: DSLContext,
    private val stickerRepository: StickerRepository,
    private val stickerRecapRepository: StickerRecapRepository,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        val photoObjectKeys = PhotoObjectKeys

        beforeSpec {
            photoStorage.clear()
        }

        afterEach {
            geminiClassifier.failureToThrow = null
            geminiClassifier.classifications = null
            photoStorage.clear()
        }

        Given("사진 그룹이 19개로(하한 미만) 요청되면") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photos =
                (0 until 19).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), PhotoContentType.JPEG) }
            val photoGroups = photos.asGroups()

            When("분석 생성을 요청하면") {
                Then("InvalidInputException(ANALYSIS-001)이 발생한다") {
                    val exception =
                        shouldThrow<InvalidInputException> {
                            analysisService.createAnalysis(board.userId.value, board.id.value, photoGroups)
                        }
                    exception.errorCode.code shouldBe "ANALYSIS-001"
                }
            }
        }

        Given("사진 그룹이 101개로(상한 초과) 요청되면") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photos =
                (0 until 101).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), PhotoContentType.JPEG) }
            val photoGroups = photos.asGroups()

            When("분석 생성을 요청하면") {
                Then("InvalidInputException(ANALYSIS-001)이 발생한다") {
                    val exception =
                        shouldThrow<InvalidInputException> {
                            analysisService.createAnalysis(board.userId.value, board.id.value, photoGroups)
                        }
                    exception.errorCode.code shouldBe "ANALYSIS-001"
                }
            }
        }

        Given("연사 그룹의 사진이 11장으로(그룹당 상한 초과) 요청되면") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val standaloneItems =
                (0 until 19).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), PhotoContentType.JPEG) }
            val burstItems =
                (0 until 11).map { i ->
                    PhotoUploadItemRequest(
                        Instant.now().plusSeconds(19 + i.toLong()),
                        PhotoContentType.JPEG,
                        isRepresentative = i == 0,
                    )
                }
            val photoGroups = standaloneItems.asGroups() + PhotoUploadGroupRequest(burstItems)

            When("분석 생성을 요청하면") {
                Then("InvalidInputException(ANALYSIS-010)이 발생한다") {
                    val exception =
                        shouldThrow<InvalidInputException> {
                            analysisService.createAnalysis(board.userId.value, board.id.value, photoGroups)
                        }
                    exception.errorCode.code shouldBe "ANALYSIS-010"
                }
            }
        }

        Given("사진 그룹이 20개(하한 경계값)로 요청되면") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photos =
                (0 until 20).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), PhotoContentType.JPEG) }
            val photoGroups = photos.asGroups()

            When("분석 생성을 요청하면") {
                Then("정상 생성된다") {
                    val result = analysisService.createAnalysis(board.userId.value, board.id.value, photoGroups)
                    result.uploads shouldHaveSize 20
                }
            }
        }

        Given("사진 그룹이 100개(상한 경계값)로 요청되면") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photos =
                (0 until 100).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), PhotoContentType.JPEG) }
            val photoGroups = photos.asGroups()

            When("분석 생성을 요청하면") {
                Then("정상 생성된다") {
                    val result = analysisService.createAnalysis(board.userId.value, board.id.value, photoGroups)
                    result.uploads shouldHaveSize 100
                }
            }
        }

        Given("연사 그룹 내 대표 사진이 정확히 1장인 요청이 오면") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val standaloneItems =
                (0 until 88).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), PhotoContentType.JPEG) }
            val burstItems =
                listOf(
                    PhotoUploadItemRequest(Instant.now().plusSeconds(88), PhotoContentType.JPEG, isRepresentative = true),
                    PhotoUploadItemRequest(Instant.now().plusSeconds(89), PhotoContentType.JPEG, isRepresentative = false),
                )
            val photoGroups = standaloneItems.asGroups() + PhotoUploadGroupRequest(burstItems)

            When("분석 생성을 요청하면") {
                Then("정상 생성되고 연사 그룹의 burstGroupId와 대표 여부가 저장된다") {
                    val result = analysisService.createAnalysis(board.userId.value, board.id.value, photoGroups)

                    val saved = photoRepository.findAllByAnalysisId(result.analysisId)
                    val burstPhotos = saved.filter { it.burstGroupId != null }
                    burstPhotos shouldHaveSize 2
                    burstPhotos.map { it.burstGroupId }.toSet() shouldHaveSize 1
                    burstPhotos.count { it.isRepresentative } shouldBe 1
                }
            }
        }

        Given("연사 그룹 내 대표 사진이 0장인 요청이 오면") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val standaloneItems =
                (0 until 88).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), PhotoContentType.JPEG) }
            val burstItems =
                listOf(
                    PhotoUploadItemRequest(Instant.now().plusSeconds(88), PhotoContentType.JPEG, isRepresentative = false),
                    PhotoUploadItemRequest(Instant.now().plusSeconds(89), PhotoContentType.JPEG, isRepresentative = false),
                )
            val photoGroups = standaloneItems.asGroups() + PhotoUploadGroupRequest(burstItems)

            When("분석 생성을 요청하면") {
                Then("InvalidInputException(ANALYSIS-009)이 발생한다") {
                    val exception =
                        shouldThrow<InvalidInputException> {
                            analysisService.createAnalysis(board.userId.value, board.id.value, photoGroups)
                        }
                    exception.errorCode.code shouldBe "ANALYSIS-009"
                }
            }
        }

        Given("연사 그룹 내 대표 사진이 2장 이상인 요청이 오면") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val standaloneItems =
                (0 until 88).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), PhotoContentType.JPEG) }
            val burstItems =
                listOf(
                    PhotoUploadItemRequest(Instant.now().plusSeconds(88), PhotoContentType.JPEG, isRepresentative = true),
                    PhotoUploadItemRequest(Instant.now().plusSeconds(89), PhotoContentType.JPEG, isRepresentative = true),
                )
            val photoGroups = standaloneItems.asGroups() + PhotoUploadGroupRequest(burstItems)

            When("분석 생성을 요청하면") {
                Then("InvalidInputException(ANALYSIS-009)이 발생한다") {
                    val exception =
                        shouldThrow<InvalidInputException> {
                            analysisService.createAnalysis(board.userId.value, board.id.value, photoGroups)
                        }
                    exception.errorCode.code shouldBe "ANALYSIS-009"
                }
            }
        }

        Given("이미 활성 분석이 있는 사용자가") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photos =
                (0 until 90).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), PhotoContentType.JPEG) }
            val photoGroups = photos.asGroups()
            analysisService.createAnalysis(board.userId.value, board.id.value, photoGroups)

            When("새로운 분석 생성을 요청하면") {
                Then("ConflictException(ANALYSIS-002)이 발생한다") {
                    val exception =
                        shouldThrow<ConflictException> {
                            analysisService.createAnalysis(board.userId.value, board.id.value, photoGroups)
                        }
                    exception.errorCode.code shouldBe "ANALYSIS-002"
                }
            }
        }

        Given("동일 사용자가 동시에 분석 생성을 요청하면") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photos =
                (0 until 90).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), PhotoContentType.JPEG) }
            val photoGroups = photos.asGroups()

            When("두 요청이 거의 동시에 들어오면") {
                val startLatch = CountDownLatch(1)
                val results = Collections.synchronizedList(mutableListOf<Result<AnalysisCreationResult>>())

                val threads =
                    (0 until 2).map {
                        thread {
                            startLatch.await()
                            results +=
                                runCatching { analysisService.createAnalysis(board.userId.value, board.id.value, photoGroups) }
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
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photos =
                (0 until 90).map { i ->
                    PhotoUploadItemRequest(
                        Instant.parse("2026-07-01T00:00:00Z").plusSeconds(i.toLong()),
                        if (i % 2 == 0) PhotoContentType.JPEG else PhotoContentType.PNG,
                    )
                }

            val result = analysisService.createAnalysis(board.userId.value, board.id.value, photos.asGroups())

            Then("UPLOADING 상태의 analysis가 생성된다") {
                val analysis = analysisRepository.findById(result.analysisId)
                analysis.shouldNotBeNull()
                analysis.status shouldBe AnalysisStatus.UPLOADING
                analysis.boardId shouldBe board.id.value
                analysis.userId shouldBe board.userId.value
            }

            Then("요청 순서와 동일하게 photo가 PENDING 상태로 생성되고 signed URL이 발급된다") {
                result.uploads shouldHaveSize photos.size
                val savedPhotos = photoRepository.findAllByAnalysisId(result.analysisId)
                savedPhotos shouldHaveSize photos.size
                savedPhotos.forEach { it.uploadStatus shouldBe UploadStatus.PENDING }

                result.uploads.forEachIndexed { i, upload ->
                    val photo = savedPhotos.first { it.id == upload.photoId }
                    photo.takenAt shouldBe photos[i].takenAt
                    photo.contentType shouldBe photos[i].contentType

                    val expectedKey = photoObjectKeys.keyFor(result.analysisId, upload.photoId, photo.contentType)
                    upload.uploadUrl shouldBe "https://fake-signed-url/$expectedKey"
                }
            }
        }

        Given("존재하지 않는 boardId로") {
            When("분석 생성을 요청하면") {
                Then("NotFoundException(BOARD-002)이 발생한다") {
                    val stranger = userRepository.saveTestUser()
                    val strangerId = stranger.id.value
                    val photos =
                        (0 until 90)
                            .map { i ->
                                PhotoUploadItemRequest(
                                    Instant.now().plusSeconds(i.toLong()),
                                    PhotoContentType.JPEG,
                                )
                            }
                    val exception =
                        shouldThrow<NotFoundException> {
                            analysisService.createAnalysis(strangerId, UUID.randomUUID(), photos.asGroups())
                        }
                    exception.errorCode.code shouldBe "BOARD-002"
                }
            }
        }

        Given("다른 사용자의 Board로") {
            val ownerBoard = boardRepository.save(userRepository.saveTestUser().id)
            val otherUser = userRepository.saveTestUser()
            val otherUserId = otherUser.id.value
            val photos =
                (0 until 90)
                    .map { i ->
                        PhotoUploadItemRequest(
                            Instant.now().plusSeconds(i.toLong()),
                            PhotoContentType.JPEG,
                        )
                    }

            When("분석 생성을 요청하면") {
                Then("NotFoundException(BOARD-002)이 발생한다") {
                    val exception =
                        shouldThrow<NotFoundException> {
                            analysisService.createAnalysis(otherUserId, ownerBoard.id.value, photos.asGroups())
                        }
                    exception.errorCode.code shouldBe "BOARD-002"
                }
            }
        }

        Given("분석이 생성되고 사진 중 일부만 실제로 업로드된 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photos =
                (0 until 90).map { i ->
                    PhotoUploadItemRequest(
                        Instant.now().plusSeconds(i.toLong()),
                        if (i % 2 == 0) PhotoContentType.JPEG else PhotoContentType.PNG,
                    )
                }
            val created = analysisService.createAnalysis(board.userId.value, board.id.value, photos.asGroups())
            val missingPhotoId = created.uploads[1].photoId
            val missingPhoto = photoRepository.findPendingByAnalysisId(created.analysisId).first { it.id == missingPhotoId }
            photoStorage.markMissing(photoObjectKeys.keyFor(created.analysisId, missingPhotoId, missingPhoto.contentType))

            When("업로드 완료를 통보하면") {
                val result = analysisService.startUpload(board.userId.value, created.analysisId)

                Then("업로드된 사진들은 COMPLETED로 바뀌고, 누락된 사진은 FAILED로 제외된다") {
                    result.uploadedCount shouldBe 89
                    result.failedCount shouldBe 1
                    result.failedPhotoIds shouldContainExactly listOf(missingPhotoId)

                    photoRepository.findPendingByAnalysisId(created.analysisId).shouldBeEmpty()
                    val failedPhoto = photoRepository.findAllByAnalysisId(created.analysisId).first { it.id == missingPhotoId }
                    failedPhoto.uploadStatus shouldBe UploadStatus.FAILED
                }

                Then("analysis의 status는 COMPLETED로 바뀌고 startedAt이 기록된다") {
                    eventually {
                        val analysis = analysisRepository.findById(created.analysisId)
                        analysis.shouldNotBeNull()
                        analysis.status shouldBe AnalysisStatus.COMPLETED
                        analysis.progress shouldBe 100
                        analysis.startedAt.shouldNotBeNull()
                    }
                }
            }
        }

        Given("분석 파이프라인이 실패하는 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photos =
                (0 until 90).map { i ->
                    PhotoUploadItemRequest(
                        Instant.now().plusSeconds(i.toLong()),
                        PhotoContentType.JPEG,
                    )
                }
            val created = analysisService.createAnalysis(board.userId.value, board.id.value, photos.asGroups())
            geminiClassifier.failureToThrow = IllegalStateException("AI 분석 실패")

            When("업로드 완료를 통보하면") {
                analysisService.startUpload(board.userId.value, created.analysisId)

                Then("analysis의 status는 FAILED로 바뀌고 시작 진행률을 유지한다") {
                    val analysis = analysisRepository.findById(created.analysisId)

                    analysis.shouldNotBeNull()
                    analysis.status shouldBe AnalysisStatus.FAILED
                    analysis.progress shouldBe 10
                    analysis.failedReason shouldBe "AI 분석 실패"
                }
            }
        }

        Given("Gemini 응답 스키마와 같은 분석 결과가 준비된 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photos =
                (0 until 90).map { i ->
                    PhotoUploadItemRequest(
                        Instant.parse("2026-07-01T00:00:00Z").plusSeconds(i.toLong()),
                        PhotoContentType.JPEG,
                    )
                }
            val created = analysisService.createAnalysis(board.userId.value, board.id.value, photos.asGroups())
            val savedPhotos = photoRepository.findAllByAnalysisId(created.analysisId)
            val themePhotoIds = savedPhotos.take(3).map { it.id }
            val sourcePhotoId = themePhotoIds[1]
            geminiClassifier.classifications =
                listOf(
                    ThemeClassification(
                        theme = "여름 여행",
                        categorizedPhotoIds = themePhotoIds,
                        recap = RecapContent(badge = "여행하루", text = "바다와 산책이 함께 남은 여행 리캡입니다."),
                        stickerTargetSubject = "파란 셔츠를 입고 웃는 사람",
                        stickerSourcePhotoId = sourcePhotoId,
                        stickerMainColor = "#FF6B6B",
                        comments =
                            listOf(
                                ThemeComment(content = "파도 소리 좋다", posX = -96.0, posY = -150.0),
                                ThemeComment(content = "여름 바다", posX = null, posY = null),
                            ),
                    ),
                )

            When("업로드 완료로 파이프라인이 실행되면") {
                analysisService.startUpload(board.userId.value, created.analysisId)

                Then("프롬프트 응답 필드가 스티커와 리캡 DB에 저장된다") {
                    eventually {
                        val analysis = analysisRepository.findById(created.analysisId)
                        analysis.shouldNotBeNull()
                        analysis.status shouldBe AnalysisStatus.COMPLETED
                        analysis.progress shouldBe 100

                        val sticker = stickerRepository.findAllByAnalysisId(AnalysisId(created.analysisId)).single()
                        sticker.type shouldBe StickerType.IMAGE
                        sticker.title shouldBe "여행하루"
                        sticker.sourcePhotoId shouldBe PhotoId(sourcePhotoId)
                        sticker.imageKey shouldBe
                            "stickers/${created.analysisId}/0-$sourcePhotoId.png"

                        sticker.summary shouldBe "바다와 산책이 함께 남은 여행 리캡입니다."

                        stickerRecapRepository.findPhotoIds(sticker.id) shouldContainExactly themePhotoIds.map(::PhotoId)
                        val comments = stickerRecapRepository.findComments(sticker.id)
                        comments shouldHaveSize 2
                        comments.map { it.content to (it.posX to it.posY) } shouldContainExactly
                            listOf(
                                "파도 소리 좋다" to (-96.0 to -150.0),
                                "여름 바다" to (null to null),
                            )
                    }
                }
            }
        }

        Given("부분 업로드된 분석이 이미 시작된 뒤 누락됐던 사진이 이후 업로드된 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photos =
                (0 until 90).map { i ->
                    PhotoUploadItemRequest(
                        Instant.now().plusSeconds(i.toLong()),
                        if (i % 2 == 0) PhotoContentType.JPEG else PhotoContentType.PNG,
                    )
                }
            val created = analysisService.createAnalysis(board.userId.value, board.id.value, photos.asGroups())
            val missingPhotoId = created.uploads[1].photoId
            val missingPhoto = photoRepository.findPendingByAnalysisId(created.analysisId).first { it.id == missingPhotoId }
            photoStorage.markMissing(photoObjectKeys.keyFor(created.analysisId, missingPhotoId, missingPhoto.contentType))
            analysisService.startUpload(board.userId.value, created.analysisId)
            photoStorage.markUploaded(photoObjectKeys.keyFor(created.analysisId, missingPhotoId, missingPhoto.contentType))

            When("다시 업로드 완료를 통보하면") {
                Then("이미 시작된 분석이므로 ConflictException(ANALYSIS-003)이 발생한다") {
                    val exception = shouldThrow<ConflictException> { analysisService.startUpload(board.userId.value, created.analysisId) }
                    exception.errorCode.code shouldBe "ANALYSIS-003"
                }
            }
        }

        Given("모든 사진이 업로드되어 분석이 시작된 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photos =
                (0 until 90).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), PhotoContentType.JPEG) }
            val created = analysisService.createAnalysis(board.userId.value, board.id.value, photos.asGroups())

            When("다시 업로드 완료를 통보하면") {
                analysisService.startUpload(board.userId.value, created.analysisId)

                Then("이미 시작된 분석이므로 ConflictException(ANALYSIS-003)이 발생한다") {
                    val exception = shouldThrow<ConflictException> { analysisService.startUpload(board.userId.value, created.analysisId) }
                    exception.errorCode.code shouldBe "ANALYSIS-003"
                }
            }
        }

        Given("분석이 생성되고 사진이 0바이트로(빈 바디로) 업로드된 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photos =
                (0 until 90).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), PhotoContentType.JPEG) }
            val created = analysisService.createAnalysis(board.userId.value, board.id.value, photos.asGroups())
            val photo = photoRepository.findPendingByAnalysisId(created.analysisId).first()
            photoStorage.markUploaded(photoObjectKeys.keyFor(created.analysisId, photo.id, photo.contentType), size = 0)

            When("업로드 완료를 통보하면") {
                val result = analysisService.startUpload(board.userId.value, created.analysisId)

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
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photos =
                (0 until 90).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), PhotoContentType.JPEG) }
            val created = analysisService.createAnalysis(board.userId.value, board.id.value, photos.asGroups())
            dslContext
                .update(ANALYSIS)
                .set(ANALYSIS.STATUS, AnalysisStatus.ANALYZING.name)
                .where(ANALYSIS.ID.eq(AnalysisId(created.analysisId)))
                .execute()

            When("업로드 완료를 통보하면") {
                Then("ConflictException(ANALYSIS-003)이 발생한다") {
                    val exception = shouldThrow<ConflictException> { analysisService.startUpload(board.userId.value, created.analysisId) }
                    exception.errorCode.code shouldBe "ANALYSIS-003"
                }
            }
        }

        Given("다른 사용자의 분석이 진행 중이 아닌 상태일 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photos =
                (0 until 90).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), PhotoContentType.JPEG) }
            val created = analysisService.createAnalysis(board.userId.value, board.id.value, photos.asGroups())
            val otherUser = userRepository.saveTestUser()
            val otherUserId = otherUser.id.value
            dslContext
                .update(ANALYSIS)
                .set(ANALYSIS.STATUS, AnalysisStatus.ANALYZING.name)
                .where(ANALYSIS.ID.eq(AnalysisId(created.analysisId)))
                .execute()

            When("업로드 완료를 통보하면") {
                Then("상태 검증보다 먼저 NotFoundException(ANALYSIS-005)이 발생한다") {
                    val exception =
                        shouldThrow<NotFoundException> {
                            analysisService.startUpload(otherUserId, created.analysisId)
                        }
                    exception.errorCode.code shouldBe "ANALYSIS-005"
                }
            }
        }

        Given("존재하지 않는 analysisId로") {
            When("업로드 완료를 통보하면") {
                Then("NotFoundException이 발생한다") {
                    val strangerUser = userRepository.saveTestUser()
                    val strangerUserId = strangerUser.id.value
                    shouldThrow<NotFoundException> {
                        analysisService.startUpload(strangerUserId, UUID.randomUUID())
                    }
                }
            }
        }

        Given("기존 분석이 COMPLETED 상태인 사용자가") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photos = (0 until 90).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), PhotoContentType.JPEG) }
            val photoGroups = photos.asGroups()
            val created = analysisService.createAnalysis(board.userId.value, board.id.value, photoGroups)
            dslContext
                .update(ANALYSIS)
                .set(ANALYSIS.STATUS, AnalysisStatus.COMPLETED.name)
                .where(ANALYSIS.ID.eq(AnalysisId(created.analysisId)))
                .execute()

            When("새로운 분석 생성을 요청하면") {
                Then("성공한다") {
                    val result = analysisService.createAnalysis(board.userId.value, board.id.value, photoGroups)
                    result.analysisId.shouldNotBeNull()
                    result.uploads shouldHaveSize 90
                }
            }
        }

        Given("UPLOADING 상태의 분석이 있을 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photos = (0 until 90).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), PhotoContentType.JPEG) }
            val created = analysisService.createAnalysis(board.userId.value, board.id.value, photos.asGroups())

            When("분석을 취소하면") {
                analysisService.cancelAnalysis(board.userId.value, created.analysisId)

                Then("analysis는 FAILED/CANCELED로 닫히고 모든 photo는 FAILED가 된다") {
                    val analysis = analysisRepository.findById(created.analysisId)
                    analysis.shouldNotBeNull()
                    analysis.status shouldBe AnalysisStatus.FAILED
                    analysis.failedReason shouldBe "CANCELED"

                    photoRepository
                        .findAllByAnalysisId(created.analysisId)
                        .map { it.uploadStatus }
                        .toSet() shouldBe setOf(UploadStatus.FAILED)
                }

                Then("커밋 후 비동기로 사진 prefix가 정리된다") {
                    eventually {
                        photoStorage.deletedPrefixes shouldContainExactly listOf(photoObjectKeys.prefixFor(created.analysisId))
                    }
                }

                Then("같은 사용자가 새로운 분석을 다시 생성할 수 있다") {
                    val result = analysisService.createAnalysis(board.userId.value, board.id.value, photos.asGroups())
                    result.analysisId.shouldNotBeNull()
                }
            }
        }

        Given("ANALYZING 상태로 전이된 분석이 있을 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photos = (0 until 90).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), PhotoContentType.JPEG) }
            val created = analysisService.createAnalysis(board.userId.value, board.id.value, photos.asGroups())
            dslContext
                .update(ANALYSIS)
                .set(ANALYSIS.STATUS, AnalysisStatus.ANALYZING.name)
                .where(ANALYSIS.ID.eq(AnalysisId(created.analysisId)))
                .execute()

            When("분석을 취소하면") {
                Then("ConflictException(ANALYSIS-004)이 발생한다") {
                    val exception =
                        shouldThrow<ConflictException> {
                            analysisService.cancelAnalysis(board.userId.value, created.analysisId)
                        }
                    exception.errorCode.code shouldBe "ANALYSIS-004"
                }
            }
        }

        Given("COMPLETED 상태로 전이된 분석이 있을 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photos = (0 until 90).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), PhotoContentType.JPEG) }
            val created = analysisService.createAnalysis(board.userId.value, board.id.value, photos.asGroups())
            dslContext
                .update(ANALYSIS)
                .set(ANALYSIS.STATUS, AnalysisStatus.COMPLETED.name)
                .where(ANALYSIS.ID.eq(AnalysisId(created.analysisId)))
                .execute()

            When("분석을 취소하면") {
                Then("ConflictException(ANALYSIS-004)이 발생한다") {
                    val exception =
                        shouldThrow<ConflictException> {
                            analysisService.cancelAnalysis(board.userId.value, created.analysisId)
                        }
                    exception.errorCode.code shouldBe "ANALYSIS-004"
                }
            }
        }

        Given("존재하지 않는 analysisId로") {
            When("취소를 요청하면") {
                Then("NotFoundException(ANALYSIS-005)이 발생한다") {
                    val stranger = userRepository.saveTestUser()
                    val exception =
                        shouldThrow<NotFoundException> {
                            analysisService.cancelAnalysis(stranger.id.value, UUID.randomUUID())
                        }
                    exception.errorCode.code shouldBe "ANALYSIS-005"
                }
            }
        }

        Given("다른 사용자의 UPLOADING 분석으로") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photos = (0 until 90).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), PhotoContentType.JPEG) }
            val created = analysisService.createAnalysis(board.userId.value, board.id.value, photos.asGroups())
            val otherUser = userRepository.saveTestUser()

            When("취소를 요청하면") {
                Then("NotFoundException(ANALYSIS-005)이 발생한다") {
                    val exception =
                        shouldThrow<NotFoundException> {
                            analysisService.cancelAnalysis(otherUser.id.value, created.analysisId)
                        }
                    exception.errorCode.code shouldBe "ANALYSIS-005"
                }
            }
        }

        Given("기존 분석이 FAILED 상태인 사용자가") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photos = (0 until 90).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), PhotoContentType.JPEG) }
            val photoGroups = photos.asGroups()
            val created = analysisService.createAnalysis(board.userId.value, board.id.value, photoGroups)
            dslContext
                .update(ANALYSIS)
                .set(ANALYSIS.STATUS, AnalysisStatus.FAILED.name)
                .where(ANALYSIS.ID.eq(AnalysisId(created.analysisId)))
                .execute()

            When("새로운 분석 생성을 요청하면") {
                Then("성공한다") {
                    val result = analysisService.createAnalysis(board.userId.value, board.id.value, photoGroups)
                    result.analysisId.shouldNotBeNull()
                    result.uploads shouldHaveSize 90
                }
            }
        }

        Given("UPLOADING 상태이고 모든 사진이 PENDING인 분석에") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photos = (0 until 90).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), PhotoContentType.JPEG) }
            val created = analysisService.createAnalysis(board.userId.value, board.id.value, photos.asGroups())

            When("업로드 URL 재발급을 요청하면") {
                Then("모든 사진에 대해 새 URL이 발급된다") {
                    val reissued = analysisService.reissueUploadUrls(board.userId.value, created.analysisId)
                    reissued shouldHaveSize 90
                    reissued.map { it.photoId }.toSet() shouldBe
                        created.uploads
                            .map { it.photoId }
                            .toSet()
                }
            }
        }

        Given("UPLOADING 상태이고 일부 사진만 업로드 완료(COMPLETED)된 분석에") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photos = (0 until 90).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), PhotoContentType.JPEG) }
            val created = analysisService.createAnalysis(board.userId.value, board.id.value, photos.asGroups())
            val completedPhotoIds =
                created.uploads
                    .take(3)
                    .map { it.photoId }
            photoRepository.markCompletedBatch(completedPhotoIds.associateWith { Instant.now() })

            When("업로드 URL 재발급을 요청하면") {
                Then("PENDING 사진에 대해서만 새 URL이 발급된다") {
                    val reissued = analysisService.reissueUploadUrls(board.userId.value, created.analysisId)
                    reissued shouldHaveSize 87
                    reissued.map { it.photoId }.toSet() shouldBe
                        (
                            created.uploads
                                .map { it.photoId }
                                .toSet() - completedPhotoIds.toSet()
                        )
                }
            }
        }

        Given("존재하지 않는 analysisId로") {
            When("업로드 URL 재발급을 요청하면") {
                Then("NotFoundException(ANALYSIS-005)이 발생한다") {
                    val stranger = userRepository.saveTestUser()
                    val exception =
                        shouldThrow<NotFoundException> {
                            analysisService.reissueUploadUrls(stranger.id.value, UUID.randomUUID())
                        }
                    exception.errorCode.code shouldBe "ANALYSIS-005"
                }
            }
        }

        Given("다른 사용자의 UPLOADING 분석으로") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photos = (0 until 90).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), PhotoContentType.JPEG) }
            val created = analysisService.createAnalysis(board.userId.value, board.id.value, photos.asGroups())
            val otherUser = userRepository.saveTestUser()

            When("업로드 URL 재발급을 요청하면") {
                Then("NotFoundException(ANALYSIS-005)이 발생한다") {
                    val exception =
                        shouldThrow<NotFoundException> {
                            analysisService.reissueUploadUrls(otherUser.id.value, created.analysisId)
                        }
                    exception.errorCode.code shouldBe "ANALYSIS-005"
                }
            }
        }

        Given("ANALYZING 상태로 전이된 분석이 있을 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photos = (0 until 90).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), PhotoContentType.JPEG) }
            val created = analysisService.createAnalysis(board.userId.value, board.id.value, photos.asGroups())
            dslContext
                .update(ANALYSIS)
                .set(ANALYSIS.STATUS, AnalysisStatus.ANALYZING.name)
                .where(ANALYSIS.ID.eq(AnalysisId(created.analysisId)))
                .execute()

            When("업로드 URL 재발급을 요청하면") {
                Then("ConflictException(ANALYSIS-003)이 발생한다") {
                    val exception =
                        shouldThrow<ConflictException> {
                            analysisService.reissueUploadUrls(board.userId.value, created.analysisId)
                        }
                    exception.errorCode.code shouldBe "ANALYSIS-003"
                }
            }
        }

        Given("FAILED 상태로 전이된 분석이 있을 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photos = (0 until 90).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), PhotoContentType.JPEG) }
            val created = analysisService.createAnalysis(board.userId.value, board.id.value, photos.asGroups())
            dslContext
                .update(ANALYSIS)
                .set(ANALYSIS.STATUS, AnalysisStatus.FAILED.name)
                .where(ANALYSIS.ID.eq(AnalysisId(created.analysisId)))
                .execute()

            When("업로드 URL 재발급을 요청하면") {
                Then("ConflictException(ANALYSIS-003)이 발생한다") {
                    val exception =
                        shouldThrow<ConflictException> {
                            analysisService.reissueUploadUrls(board.userId.value, created.analysisId)
                        }
                    exception.errorCode.code shouldBe "ANALYSIS-003"
                }
            }
        }
    })

private fun List<PhotoUploadItemRequest>.asGroups(): List<PhotoUploadGroupRequest> = map { PhotoUploadGroupRequest(listOf(it)) }

private fun eventually(assertion: () -> Unit) {
    val deadline = System.nanoTime() + 5_000_000_000

    while (true) {
        try {
            assertion()
            return
        } catch (e: AssertionError) {
            if (System.nanoTime() >= deadline) throw e
            Thread.sleep(50)
        } catch (e: NoSuchElementException) {
            if (System.nanoTime() >= deadline) throw e
            Thread.sleep(50)
        }
    }
}
