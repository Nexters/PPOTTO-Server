package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.application.model.CreateAnalysisCommand
import com.github.nexters.ppotto.analysis.application.model.UploadVerificationResult
import com.github.nexters.ppotto.analysis.domain.AnalysisErrorCode
import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import com.github.nexters.ppotto.analysis.domain.Photo
import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.analysis.domain.RecapContent
import com.github.nexters.ppotto.analysis.domain.ThemeClassification
import com.github.nexters.ppotto.analysis.domain.ThemeComment
import com.github.nexters.ppotto.analysis.domain.UploadStatus
import com.github.nexters.ppotto.analysis.infrastructure.persistence.AnalysisNotificationRepository
import com.github.nexters.ppotto.analysis.infrastructure.persistence.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.persistence.PhotoRepository
import com.github.nexters.ppotto.analysis.infrastructure.storage.PhotoObjectKeys
import com.github.nexters.ppotto.analysis.support.DEFAULT_PHOTO_GROUP_COUNT
import com.github.nexters.ppotto.analysis.support.FakePhotoStorage
import com.github.nexters.ppotto.analysis.support.FakeThemeClassifier
import com.github.nexters.ppotto.analysis.support.asGroups
import com.github.nexters.ppotto.analysis.support.burstPhotoUploadGroups
import com.github.nexters.ppotto.analysis.support.photoUploadGroups
import com.github.nexters.ppotto.analysis.support.photoUploadItems
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.error.ConflictException
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.jooq.tables.references.ANALYSIS
import com.github.nexters.ppotto.notification.domain.DevicePlatform
import com.github.nexters.ppotto.notification.infrastructure.DeviceTokenRepository
import com.github.nexters.ppotto.notification.support.FakePushNotifier
import com.github.nexters.ppotto.sticker.domain.StickerType
import com.github.nexters.ppotto.sticker.infrastructure.StickerRecapRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRepository
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.runConcurrently
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.jooq.DSLContext
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

@Suppress("LargeClass")
class AnalysisServiceTest(
    private val analysisService: AnalysisService,
    private val analysisRepository: AnalysisRepository,
    private val analysisNotificationRepository: AnalysisNotificationRepository,
    private val photoRepository: PhotoRepository,
    private val photoStorage: FakePhotoStorage,
    private val themeClassifier: FakeThemeClassifier,
    private val dslContext: DSLContext,
    private val stickerRepository: StickerRepository,
    private val stickerRecapRepository: StickerRecapRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val fakePushNotifier: FakePushNotifier,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        fun markAllUploaded(analysisId: AnalysisId): List<Photo> =
            photoRepository.findPendingByAnalysisId(analysisId).also { photoStorage.markUploaded(it) }

        fun transitionTo(
            analysisId: AnalysisId,
            status: AnalysisStatus,
        ) {
            dslContext
                .update(ANALYSIS)
                .set(ANALYSIS.STATUS, status.name)
                .where(ANALYSIS.ID.eq(analysisId))
                .execute()
        }

        Given("사진 그룹이 19개로(하한 미만) 요청되면") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photoGroups = photoUploadGroups(19)

            When("분석 생성을 요청하면") {
                Then("InvalidInputException(ANALYSIS-001)이 발생한다") {
                    val exception =
                        shouldThrow<InvalidInputException> {
                            analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoGroups))
                        }
                    exception.errorCode.code shouldBe "ANALYSIS-001"
                }
            }
        }

        Given("사진 그룹이 101개로(상한 초과) 요청되면") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photoGroups = photoUploadGroups(101)

            When("분석 생성을 요청하면") {
                Then("InvalidInputException(ANALYSIS-001)이 발생한다") {
                    val exception =
                        shouldThrow<InvalidInputException> {
                            analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoGroups))
                        }
                    exception.errorCode.code shouldBe "ANALYSIS-001"
                }
            }
        }

        Given("연사 그룹의 사진이 11장으로(그룹당 상한 초과) 요청되면") {
            val board = boardRepository.save(userRepository.saveTestUser().id)

            When("분석 생성을 요청하면") {
                Then("InvalidInputException(ANALYSIS-010)이 발생한다") {
                    val exception =
                        shouldThrow<InvalidInputException> {
                            val photoGroups = burstPhotoUploadGroups(19, listOf(true) + List(10) { false })
                            analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoGroups))
                        }
                    exception.errorCode.code shouldBe "ANALYSIS-010"
                }
            }
        }

        Given("사진 그룹이 20개(하한 경계값)로 요청되면") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photoGroups = photoUploadGroups(20)

            When("분석 생성을 요청하면") {
                val result = analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoGroups))

                Then("정상 생성된다") {
                    result.uploads shouldHaveSize 20
                }
            }
        }

        Given("사진 그룹이 100개(상한 경계값)로 요청되면") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photoGroups = photoUploadGroups(100)

            When("분석 생성을 요청하면") {
                val result = analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoGroups))

                Then("정상 생성된다") {
                    result.uploads shouldHaveSize 100
                }
            }
        }

        Given("연사 그룹 내 대표 사진이 정확히 1장인 요청이 오면") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photoGroups = burstPhotoUploadGroups(88, listOf(true, false))

            When("분석 생성을 요청하면") {
                val result = analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoGroups))

                Then("연사 그룹의 사진 2장이 하나의 burstGroupId를 공유하고 대표는 1장이다") {
                    val burstPhotos = photoRepository.findAllByAnalysisId(result.analysisId).filter { it.burstGroupId != null }
                    burstPhotos shouldHaveSize 2
                    burstPhotos.map { it.burstGroupId }.toSet() shouldHaveSize 1
                    burstPhotos.count { it.isRepresentative } shouldBe 1
                }
            }
        }

        Given("연사 그룹 내 대표 사진이 0장인 요청이 오면") {
            val board = boardRepository.save(userRepository.saveTestUser().id)

            When("분석 생성을 요청하면") {
                Then("InvalidInputException(ANALYSIS-009)이 발생한다") {
                    val exception =
                        shouldThrow<InvalidInputException> {
                            val photoGroups = burstPhotoUploadGroups(88, listOf(false, false))
                            analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoGroups))
                        }
                    exception.errorCode.code shouldBe "ANALYSIS-009"
                }
            }
        }

        Given("연사 그룹 내 대표 사진이 2장 이상인 요청이 오면") {
            val board = boardRepository.save(userRepository.saveTestUser().id)

            When("분석 생성을 요청하면") {
                Then("InvalidInputException(ANALYSIS-009)이 발생한다") {
                    val exception =
                        shouldThrow<InvalidInputException> {
                            val photoGroups = burstPhotoUploadGroups(88, listOf(true, true))
                            analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoGroups))
                        }
                    exception.errorCode.code shouldBe "ANALYSIS-009"
                }
            }
        }

        Given("이미 활성 분석이 있는 사용자가") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photoGroups = photoUploadGroups()
            analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoGroups))

            When("새로운 분석 생성을 요청하면") {
                Then("ConflictException(ANALYSIS-002)이 발생한다") {
                    val exception =
                        shouldThrow<ConflictException> {
                            analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoGroups))
                        }
                    exception.errorCode.code shouldBe "ANALYSIS-002"
                }
            }
        }

        Given("동일 사용자가 동시에 분석 생성을 요청하면") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photoGroups = photoUploadGroups()

            When("두 요청이 거의 동시에 들어오면") {
                val results =
                    runConcurrently(2) { analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoGroups)) }

                Then("정확히 하나만 성공한다") {
                    results.count { it.isSuccess } shouldBe 1
                }

                Then("나머지는 DataIntegrityViolationException이 ConflictException(ANALYSIS-002)으로 변환된다") {
                    val failure = results.single { it.isFailure }.exceptionOrNull()
                    failure.shouldBeInstanceOf<ConflictException>()
                    failure.errorCode.code shouldBe "ANALYSIS-002"
                }
            }
        }

        Given("Board가 등록된 사용자가 JPEG와 PNG를 섞은 사진 90장을 준비했을 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photos = photoUploadItems { if (it % 2 == 0) PhotoContentType.JPEG else PhotoContentType.PNG }

            When("분석 생성을 요청하면") {
                val result = analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photos.asGroups()))

                Then("요청한 보드와 사용자를 가진 UPLOADING 분석이 생성된다") {
                    val analysis = analysisRepository.findById(result.analysisId)
                    analysis.shouldNotBeNull()
                    analysis.status shouldBe AnalysisStatus.UPLOADING
                    analysis.boardId shouldBe board.id
                    analysis.userId shouldBe board.userId
                }

                Then("모든 사진이 요청 순서 그대로 PENDING으로 저장된다") {
                    val savedPhotos = photoRepository.findAllByAnalysisId(result.analysisId)
                    savedPhotos shouldHaveSize photos.size
                    savedPhotos.forEach { it.uploadStatus shouldBe UploadStatus.PENDING }

                    result.uploads.forEachIndexed { index, upload ->
                        val photo = savedPhotos.first { it.id == upload.photoId }
                        photo.takenAt shouldBe photos[index].takenAt
                        photo.contentType shouldBe photos[index].contentType
                    }
                }

                Then("사진별 오브젝트 키로 서명한 업로드 URL을 발급한다") {
                    result.uploads shouldHaveSize photos.size
                    val savedPhotos = photoRepository.findAllByAnalysisId(result.analysisId)
                    result.uploads.forEach { upload ->
                        val photo = savedPhotos.first { it.id == upload.photoId }
                        upload.uploadUrl shouldBe "https://fake-signed-url/${PhotoObjectKeys.keyFor(photo)}"
                    }
                }
            }
        }

        Given("업로드 URL 서명이 실패하는 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            photoStorage.issueUploadUrlsFailure = IllegalStateException("업로드 URL 서명 실패")

            When("분석 생성을 요청하면") {
                val exception =
                    shouldThrow<IllegalStateException> {
                        analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoUploadGroups()))
                    }

                Then("서명 예외가 그대로 호출자에게 올라온다") {
                    exception.message shouldBe "업로드 URL 서명 실패"
                }

                Then("서명 전에 커밋된 UPLOADING 분석은 그대로 남는다") {
                    val analysis = analysisRepository.findActiveByUserId(board.userId)
                    analysis.shouldNotBeNull()
                    analysis.status shouldBe AnalysisStatus.UPLOADING
                }

                Then("사진 90장도 PENDING으로 함께 커밋돼 있다") {
                    val analysis = analysisRepository.findActiveByUserId(board.userId).shouldNotBeNull()
                    photoRepository.findPendingByAnalysisId(analysis.id) shouldHaveSize DEFAULT_PHOTO_GROUP_COUNT
                }

                Then("서명이 복구되면 재발급으로 사진 90장의 URL을 다시 받는다") {
                    photoStorage.issueUploadUrlsFailure = null
                    val analysis = analysisRepository.findActiveByUserId(board.userId).shouldNotBeNull()
                    analysisService.reissueUploadUrls(board.userId, analysis.id) shouldHaveSize DEFAULT_PHOTO_GROUP_COUNT
                }
            }
        }

        Given("존재하지 않는 boardId로") {
            val stranger = userRepository.saveTestUser()

            When("분석 생성을 요청하면") {
                Then("NotFoundException(BOARD-002)이 발생한다") {
                    val exception =
                        shouldThrow<NotFoundException> {
                            analysisService.createAnalysis(
                                stranger.id,
                                BoardId(UUID.randomUUID()),
                                CreateAnalysisCommand(photoUploadGroups()),
                            )
                        }
                    exception.errorCode.code shouldBe "BOARD-002"
                }
            }
        }

        Given("다른 사용자의 Board로") {
            val ownerBoard = boardRepository.save(userRepository.saveTestUser().id)
            val otherUserId = userRepository.saveTestUser().id

            When("분석 생성을 요청하면") {
                Then("NotFoundException(BOARD-002)이 발생한다") {
                    val exception =
                        shouldThrow<NotFoundException> {
                            analysisService.createAnalysis(otherUserId, ownerBoard.id, CreateAnalysisCommand(photoUploadGroups()))
                        }
                    exception.errorCode.code shouldBe "BOARD-002"
                }
            }
        }

        Given("분석이 생성되고 사진이 한 장도 업로드되지 않은 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val created = analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoUploadGroups()))

            When("업로드 완료를 통보하면") {
                val exception = shouldThrow<ConflictException> { analysisService.startUpload(board.userId, created.analysisId) }

                Then("ConflictException(ANALYSIS-008)이 발생한다") {
                    exception.errorCode.code shouldBe "ANALYSIS-008"
                }

                Then("분석은 UPLOADING으로 남아 다시 업로드할 수 있다") {
                    analysisRepository.findById(created.analysisId)!!.status shouldBe AnalysisStatus.UPLOADING
                    photoRepository.findPendingByAnalysisId(created.analysisId) shouldHaveSize DEFAULT_PHOTO_GROUP_COUNT
                }
            }
        }

        Given("분석이 생성되고 사진 중 일부만 실제로 업로드된 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photos = photoUploadItems { if (it % 2 == 0) PhotoContentType.JPEG else PhotoContentType.PNG }
            val created = analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photos.asGroups()))
            val missingPhotoId = created.uploads[1].photoId
            val missingPhoto = markAllUploaded(created.analysisId).first { it.id == missingPhotoId }
            photoStorage.markMissing(missingPhoto)

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

                Then("이어서 동기 실행된 파이프라인까지 끝나 분석은 진행률 100의 COMPLETED로 마감된다") {
                    val analysis = analysisRepository.findById(created.analysisId)
                    analysis.shouldNotBeNull()
                    analysis.status shouldBe AnalysisStatus.COMPLETED
                    analysis.progress shouldBe 100
                    analysis.startedAt.shouldNotBeNull()
                }
            }
        }

        Given("분석 파이프라인이 실패하는 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val created = analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoUploadGroups()))
            markAllUploaded(created.analysisId)
            themeClassifier.failureToThrow = IllegalStateException("AI 분석 실패")

            When("업로드 완료를 통보하면") {
                analysisService.startUpload(board.userId, created.analysisId)

                Then("analysis의 status는 FAILED로 바뀌고 시작 진행률을 유지한다") {
                    val analysis = analysisRepository.findById(created.analysisId)

                    analysis.shouldNotBeNull()
                    analysis.status shouldBe AnalysisStatus.FAILED
                    analysis.progress shouldBe 10
                    analysis.failedReason shouldContain "[gemini-classification]"
                    analysis.failedCode shouldBe AnalysisErrorCode.INTERNAL_ERROR
                    analysis.failedReason shouldContain AnalysisErrorCode.INTERNAL_ERROR.message
                }
            }
        }

        Given("Gemini 응답 스키마와 같은 분석 결과가 준비된 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val created = analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoUploadGroups()))
            val savedPhotos = markAllUploaded(created.analysisId)
            val themePhotoIds = savedPhotos.take(3).map { it.id }
            val sourcePhotoId = themePhotoIds[1]
            themeClassifier.classifications =
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
                analysisService.startUpload(board.userId, created.analysisId)

                Then("분석은 진행률 100의 COMPLETED로 마감된다") {
                    val analysis = analysisRepository.findById(created.analysisId)
                    analysis.shouldNotBeNull()
                    analysis.status shouldBe AnalysisStatus.COMPLETED
                    analysis.progress shouldBe 100
                }

                Then("recap.badge는 스티커 제목, recap.text는 요약으로 저장된다") {
                    val sticker = stickerRepository.findAllByAnalysisId(created.analysisId).single()
                    sticker.type shouldBe StickerType.IMAGE
                    sticker.title shouldBe "여행하루"
                    sticker.summary shouldBe "바다와 산책이 함께 남은 여행 리캡입니다."
                }

                Then("스티커 이미지 키는 분석과 테마 순번, 원본 사진으로 만든 키를 그대로 쓴다") {
                    val sticker = stickerRepository.findAllByAnalysisId(created.analysisId).single()
                    sticker.sourcePhotoId shouldBe sourcePhotoId
                    sticker.imageKey shouldBe "stickers/${created.analysisId}/0-$sourcePhotoId.png"
                }

                Then("분류된 사진과 코멘트가 리캡에 그대로 연결된다") {
                    val sticker = stickerRepository.findAllByAnalysisId(created.analysisId).single()
                    stickerRecapRepository.findPhotoIds(sticker.id) shouldContainExactly themePhotoIds
                    stickerRecapRepository.findComments(sticker.id).map { it.content to (it.posX to it.posY) } shouldContainExactly
                        listOf(
                            "파도 소리 좋다" to (-96.0 to -150.0),
                            "여름 바다" to (null to null),
                        )
                }
            }
        }

        Given("부분 업로드된 분석이 이미 시작된 뒤 누락됐던 사진이 이후 업로드된 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val created = analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoUploadGroups()))
            val missingPhotoId = created.uploads[1].photoId
            val missingPhoto = markAllUploaded(created.analysisId).first { it.id == missingPhotoId }
            photoStorage.markMissing(missingPhoto)
            analysisService.startUpload(board.userId, created.analysisId)
            photoStorage.markUploaded(missingPhoto)

            When("다시 업로드 완료를 통보하면") {
                Then("이미 시작된 분석이므로 ConflictException(ANALYSIS-003)이 발생한다") {
                    val exception = shouldThrow<ConflictException> { analysisService.startUpload(board.userId, created.analysisId) }
                    exception.errorCode.code shouldBe "ANALYSIS-003"
                }
            }
        }

        Given("모든 사진이 업로드되어 분석이 시작된 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val created = analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoUploadGroups()))
            markAllUploaded(created.analysisId)
            analysisService.startUpload(board.userId, created.analysisId)

            When("다시 업로드 완료를 통보하면") {
                Then("이미 시작된 분석이므로 ConflictException(ANALYSIS-003)이 발생한다") {
                    val exception = shouldThrow<ConflictException> { analysisService.startUpload(board.userId, created.analysisId) }
                    exception.errorCode.code shouldBe "ANALYSIS-003"
                }
            }
        }

        Given("업로드 검증이 오브젝트 조회 지점에서 멈춰 있는 분석에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val created = analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoUploadGroups()))
            markAllUploaded(created.analysisId)
            val reachedStorage = CountDownLatch(1)
            val resumeVerification = CountDownLatch(1)
            photoStorage.onUploadedObjects = {
                reachedStorage.countDown()
                check(resumeVerification.await(10, TimeUnit.SECONDS)) { "업로드 검증 재개 신호가 오지 않았습니다." }
            }

            When("잠금 이전 상태 검증을 통과한 뒤 다른 요청이 분석을 ANALYZING으로 전이시키면") {
                val outcome = AtomicReference<Result<UploadVerificationResult>>()
                val verifier =
                    thread { outcome.set(runCatching { analysisService.startUpload(board.userId, created.analysisId) }) }
                check(reachedStorage.await(10, TimeUnit.SECONDS)) { "업로드 검증이 오브젝트 조회까지 오지 않았습니다." }
                analysisRepository.markAnalyzing(created.analysisId, Instant.now())
                resumeVerification.countDown()
                verifier.join()

                Then("행 잠금 뒤 상태를 다시 확인해 ConflictException(ANALYSIS-003)이 발생한다") {
                    val failure = outcome.get().exceptionOrNull()
                    failure.shouldBeInstanceOf<ConflictException>()
                    failure.errorCode.code shouldBe "ANALYSIS-003"
                }

                Then("사진은 한 장도 COMPLETED로 확정되지 않는다") {
                    photoRepository.findPendingByAnalysisId(created.analysisId) shouldHaveSize DEFAULT_PHOTO_GROUP_COUNT
                }
            }
        }

        Given("분석이 생성되고 사진이 0바이트로(빈 바디로) 업로드된 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val created = analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoUploadGroups()))
            val photo = markAllUploaded(created.analysisId).first()
            photoStorage.markUploaded(photo, size = 0)

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
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val created = analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoUploadGroups()))
            transitionTo(created.analysisId, AnalysisStatus.ANALYZING)

            When("업로드 완료를 통보하면") {
                Then("ConflictException(ANALYSIS-003)이 발생한다") {
                    val exception = shouldThrow<ConflictException> { analysisService.startUpload(board.userId, created.analysisId) }
                    exception.errorCode.code shouldBe "ANALYSIS-003"
                }
            }
        }

        Given("다른 사용자의 분석이 진행 중이 아닌 상태일 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val created = analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoUploadGroups()))
            val otherUserId = userRepository.saveTestUser().id
            transitionTo(created.analysisId, AnalysisStatus.ANALYZING)

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

        Given("존재하지 않는 analysisId를 가진 사용자가 업로드 완료를 통보할 때") {
            val strangerUserId = userRepository.saveTestUser().id

            When("업로드 완료를 통보하면") {
                Then("NotFoundException(ANALYSIS-005)이 발생한다") {
                    val exception =
                        shouldThrow<NotFoundException> {
                            analysisService.startUpload(strangerUserId, AnalysisId(UUID.randomUUID()))
                        }
                    exception.errorCode.code shouldBe "ANALYSIS-005"
                }
            }
        }

        Given("기존 분석이 COMPLETED 상태인 사용자가") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photoGroups = photoUploadGroups()
            val created = analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoGroups))
            transitionTo(created.analysisId, AnalysisStatus.COMPLETED)

            When("새로운 분석 생성을 요청하면") {
                val result = analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoGroups))

                Then("성공한다") {
                    result.analysisId.shouldNotBeNull()
                    result.uploads shouldHaveSize DEFAULT_PHOTO_GROUP_COUNT
                }
            }
        }

        Given("완료 알림을 신청해둔 UPLOADING 상태의 분석을 취소할 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photoGroups = photoUploadGroups()
            val created = analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoGroups))
            deviceTokenRepository.upsert(board.userId, "device-1", DevicePlatform.IOS, "fcm-token-1")
            analysisNotificationRepository.markRequested(created.analysisId, Instant.now())

            When("분석을 취소하면") {
                analysisService.cancelAnalysis(board.userId, created.analysisId)

                Then("analysis는 FAILED/CANCELED로 닫히고 모든 photo는 FAILED가 된다") {
                    val analysis = analysisRepository.findById(created.analysisId)
                    analysis.shouldNotBeNull()
                    analysis.status shouldBe AnalysisStatus.FAILED
                    analysis.failedReason shouldBe "CANCELED"
                    analysis.failedCode shouldBe AnalysisErrorCode.ANALYSIS_CANCELED

                    photoRepository
                        .findAllByAnalysisId(created.analysisId)
                        .map { it.uploadStatus }
                        .toSet() shouldBe setOf(UploadStatus.FAILED)
                }

                Then("커밋 후 동기적으로 해당 분석의 사진 오브젝트가 정리된다") {
                    photoStorage.deletedAnalysisIds shouldContainExactly listOf(created.analysisId)
                }

                Then("같은 사용자가 새로운 분석을 다시 생성할 수 있다") {
                    val result = analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoGroups))
                    result.analysisId.shouldNotBeNull()
                }

                Then("알림을 신청했어도 본인이 취소한 것이므로 실패 알림을 보내지 않는다") {
                    fakePushNotifier.sentMessages.shouldBeEmpty()
                }
            }
        }

        Given("사진 오브젝트 정리가 실패하는 UPLOADING 분석에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photoGroups = photoUploadGroups()
            val created = analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoGroups))
            photoStorage.deleteAllFailure = IllegalStateException("오브젝트 삭제 실패")

            When("분석을 취소하면") {
                analysisService.cancelAnalysis(board.userId, created.analysisId)

                Then("정리 실패는 로그로만 남고 취소는 그대로 커밋된다") {
                    val analysis = analysisRepository.findById(created.analysisId)
                    analysis.shouldNotBeNull()
                    analysis.status shouldBe AnalysisStatus.FAILED
                    analysis.failedReason shouldBe "CANCELED"
                }

                Then("정리에 실패해도 같은 사용자가 새로운 분석을 다시 생성할 수 있다") {
                    val result = analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoGroups))
                    result.analysisId.shouldNotBeNull()
                }
            }
        }

        Given("ANALYZING 상태로 전이된 분석을 취소할 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val created = analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoUploadGroups()))
            transitionTo(created.analysisId, AnalysisStatus.ANALYZING)

            When("분석을 취소하면") {
                analysisService.cancelAnalysis(board.userId, created.analysisId)

                Then("analysis는 FAILED/CANCELED로 닫히고 모든 photo는 FAILED가 된다") {
                    val analysis = analysisRepository.findById(created.analysisId)
                    analysis.shouldNotBeNull()
                    analysis.status shouldBe AnalysisStatus.FAILED
                    analysis.failedReason shouldBe "CANCELED"
                    analysis.failedCode shouldBe AnalysisErrorCode.ANALYSIS_CANCELED

                    photoRepository
                        .findAllByAnalysisId(created.analysisId)
                        .map { it.uploadStatus }
                        .toSet() shouldBe setOf(UploadStatus.FAILED)
                }
            }
        }

        Given("COMPLETED 상태로 전이된 분석을 취소할 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val created = analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoUploadGroups()))
            transitionTo(created.analysisId, AnalysisStatus.COMPLETED)

            When("분석을 취소하면") {
                Then("ConflictException(ANALYSIS-004)이 발생한다") {
                    val exception =
                        shouldThrow<ConflictException> {
                            analysisService.cancelAnalysis(board.userId, created.analysisId)
                        }
                    exception.errorCode.code shouldBe "ANALYSIS-004"
                }
            }
        }

        Given("존재하지 않는 analysisId를 가진 사용자가 취소를 요청할 때") {
            val stranger = userRepository.saveTestUser()

            When("취소를 요청하면") {
                Then("NotFoundException(ANALYSIS-005)이 발생한다") {
                    val exception =
                        shouldThrow<NotFoundException> {
                            analysisService.cancelAnalysis(stranger.id, AnalysisId(UUID.randomUUID()))
                        }
                    exception.errorCode.code shouldBe "ANALYSIS-005"
                }
            }
        }

        Given("다른 사용자의 UPLOADING 분석을 취소하려 할 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val created = analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoUploadGroups()))
            val otherUser = userRepository.saveTestUser()

            When("취소를 요청하면") {
                Then("NotFoundException(ANALYSIS-005)이 발생한다") {
                    val exception =
                        shouldThrow<NotFoundException> {
                            analysisService.cancelAnalysis(otherUser.id, created.analysisId)
                        }
                    exception.errorCode.code shouldBe "ANALYSIS-005"
                }
            }
        }

        Given("기존 분석이 FAILED 상태인 사용자가") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val photoGroups = photoUploadGroups()
            val created = analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoGroups))
            transitionTo(created.analysisId, AnalysisStatus.FAILED)

            When("새로운 분석 생성을 요청하면") {
                val result = analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoGroups))

                Then("성공한다") {
                    result.analysisId.shouldNotBeNull()
                    result.uploads shouldHaveSize DEFAULT_PHOTO_GROUP_COUNT
                }
            }
        }

        Given("UPLOADING 상태이고 모든 사진이 PENDING인 분석에") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val created = analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoUploadGroups()))

            When("업로드 URL 재발급을 요청하면") {
                val reissued = analysisService.reissueUploadUrls(board.userId, created.analysisId)

                Then("모든 사진에 대해 새 URL이 발급된다") {
                    reissued shouldHaveSize DEFAULT_PHOTO_GROUP_COUNT
                    reissued.map { it.photoId }.toSet() shouldBe
                        created.uploads
                            .map { it.photoId }
                            .toSet()
                }
            }
        }

        Given("UPLOADING 상태이고 일부 사진만 업로드 완료(COMPLETED)된 분석에") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val created = analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoUploadGroups()))
            val completedPhotoIds =
                created.uploads
                    .take(3)
                    .map { it.photoId }
            photoRepository.markCompletedBatch(completedPhotoIds.associateWith { Instant.now() })

            When("업로드 URL 재발급을 요청하면") {
                val reissued = analysisService.reissueUploadUrls(board.userId, created.analysisId)

                Then("PENDING 사진에 대해서만 새 URL이 발급된다") {
                    reissued shouldHaveSize 87
                    reissued.map { it.photoId }.toSet() shouldBe
                        created.uploads
                            .map { it.photoId }
                            .toSet() - completedPhotoIds.toSet()
                }
            }
        }

        Given("존재하지 않는 analysisId를 가진 사용자가 업로드 URL 재발급을 요청할 때") {
            val stranger = userRepository.saveTestUser()

            When("업로드 URL 재발급을 요청하면") {
                Then("NotFoundException(ANALYSIS-005)이 발생한다") {
                    val exception =
                        shouldThrow<NotFoundException> {
                            analysisService.reissueUploadUrls(stranger.id, AnalysisId(UUID.randomUUID()))
                        }
                    exception.errorCode.code shouldBe "ANALYSIS-005"
                }
            }
        }

        Given("다른 사용자의 UPLOADING 분석으로 업로드 URL 재발급을 요청할 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val created = analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoUploadGroups()))
            val otherUser = userRepository.saveTestUser()

            When("업로드 URL 재발급을 요청하면") {
                Then("NotFoundException(ANALYSIS-005)이 발생한다") {
                    val exception =
                        shouldThrow<NotFoundException> {
                            analysisService.reissueUploadUrls(otherUser.id, created.analysisId)
                        }
                    exception.errorCode.code shouldBe "ANALYSIS-005"
                }
            }
        }

        Given("ANALYZING 상태로 전이된 분석에 업로드 URL 재발급을 요청할 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val created = analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoUploadGroups()))
            transitionTo(created.analysisId, AnalysisStatus.ANALYZING)

            When("업로드 URL 재발급을 요청하면") {
                Then("ConflictException(ANALYSIS-003)이 발생한다") {
                    val exception =
                        shouldThrow<ConflictException> {
                            analysisService.reissueUploadUrls(board.userId, created.analysisId)
                        }
                    exception.errorCode.code shouldBe "ANALYSIS-003"
                }
            }
        }

        Given("FAILED 상태로 전이된 분석에 업로드 URL 재발급을 요청할 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val created = analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoUploadGroups()))
            transitionTo(created.analysisId, AnalysisStatus.FAILED)

            When("업로드 URL 재발급을 요청하면") {
                Then("ConflictException(ANALYSIS-003)이 발생한다") {
                    val exception =
                        shouldThrow<ConflictException> {
                            analysisService.reissueUploadUrls(board.userId, created.analysisId)
                        }
                    exception.errorCode.code shouldBe "ANALYSIS-003"
                }
            }
        }
    })
