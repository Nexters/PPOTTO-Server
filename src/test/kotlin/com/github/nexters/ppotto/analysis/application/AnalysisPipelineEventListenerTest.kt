package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.AnalysisErrorCode
import com.github.nexters.ppotto.analysis.domain.AnalysisStartRequestedEvent
import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.analysis.domain.PhotoRef
import com.github.nexters.ppotto.analysis.domain.RecapContent
import com.github.nexters.ppotto.analysis.domain.ThemeClassification
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.PhotoCreate
import com.github.nexters.ppotto.analysis.infrastructure.PhotoRepository
import com.github.nexters.ppotto.analysis.support.FakeStickerGenerator
import com.github.nexters.ppotto.analysis.support.FakeThemeClassifier
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.error.BusinessException
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.notification.domain.DevicePlatform
import com.github.nexters.ppotto.notification.infrastructure.DeviceTokenRepository
import com.github.nexters.ppotto.notification.support.FakePushNotifier
import com.github.nexters.ppotto.notification.support.SentPush
import com.github.nexters.ppotto.sticker.application.AnalysisResultSaveService
import com.github.nexters.ppotto.sticker.infrastructure.StickerRepository
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.jooq.DSLContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

private const val NO_COMPLETED_CONSTRAINT = "test_analysis_never_completed"

class AnalysisPipelineEventListenerTest(
    private val analysisPipelineEventListener: AnalysisPipelineEventListener,
    private val analysisPipelineService: AnalysisPipelineService,
    private val analysisResultSaveService: AnalysisResultSaveService,
    private val analysisRepository: AnalysisRepository,
    private val photoRepository: PhotoRepository,
    private val stickerRepository: StickerRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val themeClassifier: FakeThemeClassifier,
    private val stickerGenerator: FakeStickerGenerator,
    private val fakePushNotifier: FakePushNotifier,
    private val eventPublisher: ApplicationEventPublisher,
    private val transactionTemplate: TransactionTemplate,
    private val transactionManager: PlatformTransactionManager,
    private val dslContext: DSLContext,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        fun analyzingAnalysis(photoCount: Int = 1): Pair<AnalysisId, List<PhotoRef>> {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)
            val analysis = analysisRepository.save(user.id, board.id)
            val photos =
                photoRepository.saveAll(
                    analysis.id,
                    board.id,
                    List(photoCount) { PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-01T00:00:00Z")) },
                )
            photoRepository.markCompletedBatch(photos.associate { it.id to Instant.now() })
            analysisRepository.markAnalyzing(analysis.id, Instant.now())
            deviceTokenRepository.upsert(user.id, "device-1", DevicePlatform.IOS, "fcm-token-1")
            return analysis.id to photos.map { PhotoRef(it.id, "gs://bucket/${it.id}.jpg", "image/jpeg") }
        }

        Given("업로드가 완료된 분석과 등록된 디바이스 토큰이 있는 상태에서") {
            val (analysisId, photoRefs) = analyzingAnalysis()

            When("파이프라인이 성공적으로 완료되면") {
                analysisPipelineEventListener.handle(AnalysisStartRequestedEvent(analysisId, photoRefs))

                Then("분석 상태가 COMPLETED로 바뀐다") {
                    analysisRepository.findById(analysisId)!!.status shouldBe AnalysisStatus.COMPLETED
                }

                Then("파이프라인이 만든 테마가 스티커로 저장된다") {
                    val sticker = stickerRepository.findAllByAnalysisId(analysisId).single()
                    sticker.title shouldBe "테스트뱃지"
                    sticker.summary shouldBe "테스트 리캡 문구입니다."
                }

                Then("완료 알림이 등록된 디바이스 토큰으로 발송된다") {
                    val completedMessages = fakePushNotifier.messagesFor(analysisId, "ANALYSIS_COMPLETED")
                    completedMessages shouldHaveSize 1
                    completedMessages.single().tokens shouldBe listOf("fcm-token-1")
                }
            }

            When("파이프라인이 실패하면") {
                themeClassifier.failureToThrow = BusinessException(AnalysisErrorCode.CLASSIFICATION_FAILED, "provider-secret")
                analysisPipelineEventListener.handle(AnalysisStartRequestedEvent(analysisId, photoRefs))

                Then("분석 상태가 FAILED로 바뀐다") {
                    analysisRepository.findById(analysisId)!!.status shouldBe AnalysisStatus.FAILED
                    analysisRepository.findById(analysisId)!!.failedCode shouldBe AnalysisErrorCode.CLASSIFICATION_FAILED
                    analysisRepository.findById(analysisId)!!.failedReason!! shouldNotContain "provider-secret"
                }

                Then("스티커는 하나도 저장되지 않는다") {
                    stickerRepository.findAllByAnalysisId(analysisId).shouldBeEmpty()
                }

                Then("실패 알림이 등록된 디바이스 토큰으로 발송된다") {
                    val failedMessages = fakePushNotifier.messagesFor(analysisId, "ANALYSIS_FAILED")
                    failedMessages shouldHaveSize 1
                    failedMessages.single().tokens shouldBe listOf("fcm-token-1")
                }
            }

            When("모든 테마의 스티커 생성이 실패해 저장할 스티커가 하나도 없으면") {
                stickerGenerator.onGenerate = { throw IllegalStateException("배경 제거 실패") }
                analysisPipelineEventListener.handle(AnalysisStartRequestedEvent(analysisId, photoRefs))
                val analysis = analysisRepository.findById(analysisId)!!

                Then("스티커 저장 없이 전체 생성 실패로 마감한다") {
                    stickerRepository.findAllByAnalysisId(analysisId).shouldBeEmpty()
                    analysis.status shouldBe AnalysisStatus.FAILED
                    analysis.failedCode shouldBe AnalysisErrorCode.STICKER_GENERATION_FAILED
                    analysis.failedReason shouldContain AnalysisErrorCode.STICKER_GENERATION_FAILED.message
                    fakePushNotifier.messagesFor(analysisId, "ANALYSIS_FAILED") shouldHaveSize 1
                    fakePushNotifier.messagesFor(analysisId, "ANALYSIS_COMPLETED").shouldBeEmpty()
                }
            }

            When("모든 테마에서 피사체가 없다고 판정하면") {
                themeClassifier.onVerify = { _, _ -> null }
                analysisPipelineEventListener.handle(AnalysisStartRequestedEvent(analysisId, photoRefs))

                Then("피사체 없음 코드로 실패 처리한다") {
                    analysisRepository.findById(analysisId)!!.status shouldBe AnalysisStatus.FAILED
                    analysisRepository.findById(analysisId)!!.failedCode shouldBe AnalysisErrorCode.NO_STICKER_SUBJECT
                    stickerRepository.findAllByAnalysisId(analysisId).shouldBeEmpty()
                }
            }

            When("분류 응답 검증에 실패하면") {
                themeClassifier.failureToThrow = BusinessException(AnalysisErrorCode.INVALID_GEMINI_RESPONSE)
                analysisPipelineEventListener.handle(AnalysisStartRequestedEvent(analysisId, photoRefs))

                Then("기존 분류 응답 오류 코드를 보존한다") {
                    analysisRepository.findById(analysisId)!!.failedCode shouldBe AnalysisErrorCode.INVALID_GEMINI_RESPONSE
                }
            }

            When("예상하지 못한 내부 예외가 발생하면") {
                themeClassifier.failureToThrow = IllegalStateException("internal-secret")
                analysisPipelineEventListener.handle(AnalysisStartRequestedEvent(analysisId, photoRefs))

                Then("내부 오류 코드만 안전한 사유와 함께 기록한다") {
                    analysisRepository.findById(analysisId)!!.failedCode shouldBe AnalysisErrorCode.INTERNAL_ERROR
                    analysisRepository.findById(analysisId)!!.failedReason!! shouldNotContain "internal-secret"
                }
            }

            When("파이프라인 도중 분석이 실패로 종료되면") {
                stickerGenerator.onGenerate = {
                    analysisRepository.markFailed(analysisId, "분석 처리 중단", AnalysisErrorCode.INTERNAL_ERROR)
                }
                analysisPipelineEventListener.handle(AnalysisStartRequestedEvent(analysisId, photoRefs))

                Then("늦게 완성된 스티커를 저장하거나 완료 알림을 보내지 않는다") {
                    analysisRepository.findById(analysisId)!!.status shouldBe AnalysisStatus.FAILED
                    analysisRepository.findById(analysisId)!!.failedCode shouldBe AnalysisErrorCode.INTERNAL_ERROR
                    stickerRepository.findAllByAnalysisId(analysisId).shouldBeEmpty()
                    fakePushNotifier.messagesFor(analysisId, "ANALYSIS_COMPLETED").shouldBeEmpty()
                    fakePushNotifier.messagesFor(analysisId, "ANALYSIS_FAILED").shouldBeEmpty()
                }
            }
        }

        Given("두 테마 중 한 테마의 스티커 생성만 실패하는 분석에서") {
            val (analysisId, photoRefs) = analyzingAnalysis(photoCount = 2)
            themeClassifier.classifications =
                photoRefs.mapIndexed { index, photo ->
                    ThemeClassification(
                        theme = "테마$index",
                        categorizedPhotoIds = listOf(photo.photoId),
                        recap = RecapContent(badge = "뱃지$index", text = "리캡$index"),
                        stickerTargetSubject = "피사체$index",
                        stickerSourcePhotoId = photo.photoId,
                        stickerMainColor = "#FF6B6B",
                        comments = emptyList(),
                    )
                }
            stickerGenerator.onGenerate = { if (it == "피사체1") throw IllegalStateException("배경 제거 실패") }

            When("파이프라인을 실행하면") {
                analysisPipelineEventListener.handle(AnalysisStartRequestedEvent(analysisId, photoRefs))

                Then("성공한 스티커만 저장하고 실패 코드 없이 완료 처리한다") {
                    val analysis = analysisRepository.findById(analysisId)!!
                    analysis.status shouldBe AnalysisStatus.COMPLETED
                    analysis.failedCode shouldBe null
                    stickerRepository
                        .findAllByAnalysisId(analysisId)
                        .single()
                        .title shouldBe "뱃지0"
                    fakePushNotifier.messagesFor(analysisId, "ANALYSIS_COMPLETED") shouldHaveSize 1
                    fakePushNotifier.messagesFor(analysisId, "ANALYSIS_FAILED").shouldBeEmpty()
                }
            }
        }

        Given("스티커 저장 직후의 완료 처리만 실패하도록 제약을 건 분석에서") {
            val (analysisId, photoRefs) = analyzingAnalysis()

            When("파이프라인 결과를 저장하면") {
                dslContext.execute("ALTER TABLE analysis ADD CONSTRAINT $NO_COMPLETED_CONSTRAINT CHECK (status <> 'COMPLETED')")
                try {
                    analysisPipelineEventListener.handle(AnalysisStartRequestedEvent(analysisId, photoRefs))
                } finally {
                    dslContext.execute("ALTER TABLE analysis DROP CONSTRAINT IF EXISTS $NO_COMPLETED_CONSTRAINT")
                }

                Then("같은 트랜잭션이라 저장됐던 스티커도 함께 롤백된다") {
                    stickerRepository.findAllByAnalysisId(analysisId).shouldBeEmpty()
                }

                Then("분석은 COMPLETED가 아니라 FAILED로 남는다") {
                    analysisRepository.findById(analysisId)!!.status shouldBe AnalysisStatus.FAILED
                    analysisRepository.findById(analysisId)!!.failedCode shouldBe AnalysisErrorCode.RESULT_SAVE_FAILED
                }
            }
        }

        Given("푸시 이벤트 발행이 항상 실패하는 리스너에서") {
            val (analysisId, photoRefs) = analyzingAnalysis()
            val listener =
                AnalysisPipelineEventListener(
                    analysisPipelineService,
                    analysisRepository,
                    analysisResultSaveService,
                    ApplicationEventPublisher { throw IllegalStateException("푸시 이벤트 발행 실패") },
                    transactionTemplate,
                    transactionManager,
                )

            When("파이프라인이 성공적으로 완료되면") {
                listener.handle(AnalysisStartRequestedEvent(analysisId, photoRefs))

                Then("알림 실패를 삼키고 분석은 COMPLETED로 마감한다") {
                    analysisRepository.findById(analysisId)!!.status shouldBe AnalysisStatus.COMPLETED
                    stickerRepository.findAllByAnalysisId(analysisId) shouldHaveSize 1
                }
            }
        }

        Given("진행률 갱신 트랜잭션이 항상 실패하는 리스너에서") {
            val (analysisId, photoRefs) = analyzingAnalysis()
            val listener =
                AnalysisPipelineEventListener(
                    analysisPipelineService,
                    analysisRepository,
                    analysisResultSaveService,
                    eventPublisher,
                    transactionTemplate,
                    FailingTransactionManager,
                )

            When("파이프라인이 성공적으로 완료되면") {
                listener.handle(AnalysisStartRequestedEvent(analysisId, photoRefs))

                Then("진행률 갱신 실패를 삼키고 분석은 COMPLETED로 마감한다") {
                    analysisRepository.findById(analysisId)!!.status shouldBe AnalysisStatus.COMPLETED
                    stickerRepository.findAllByAnalysisId(analysisId) shouldHaveSize 1
                }
            }
        }
    })

private object FailingTransactionManager : PlatformTransactionManager {
    override fun getTransaction(definition: TransactionDefinition?): TransactionStatus = error("진행률 트랜잭션을 열 수 없습니다.")

    override fun commit(status: TransactionStatus) = Unit

    override fun rollback(status: TransactionStatus) = Unit
}

private fun FakePushNotifier.messagesFor(
    analysisId: AnalysisId,
    type: String,
): List<SentPush> = sentMessages.filter { it.data["analysisId"] == analysisId.toString() && it.data["type"] == type }
