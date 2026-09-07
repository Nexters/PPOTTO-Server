package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.AnalysisStartRequestedEvent
import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.analysis.domain.PhotoRef
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.PhotoCreate
import com.github.nexters.ppotto.analysis.infrastructure.PhotoRepository
import com.github.nexters.ppotto.analysis.support.AnalysisTestConfig
import com.github.nexters.ppotto.analysis.support.FakeGeminiClassifier
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.notification.domain.DevicePlatform
import com.github.nexters.ppotto.notification.infrastructure.DeviceTokenRepository
import com.github.nexters.ppotto.notification.support.FakePushNotifier
import com.github.nexters.ppotto.notification.support.NotificationTestConfig
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.context.annotation.Import
import java.time.Instant

@Import(AnalysisTestConfig::class, NotificationTestConfig::class)
class AnalysisPipelineEventListenerTest(
    private val analysisPipelineEventListener: AnalysisPipelineEventListener,
    private val analysisRepository: AnalysisRepository,
    private val photoRepository: PhotoRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val geminiClassifier: FakeGeminiClassifier,
    private val fakePushNotifier: FakePushNotifier,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        afterEach {
            geminiClassifier.failureToThrow = null
        }

        Given("업로드가 완료된 분석과 등록된 디바이스 토큰이 있는 상태에서") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)
            val analysis = analysisRepository.save(user.id.value, board.id.value)
            val photos =
                photoRepository.saveAll(
                    analysis.id,
                    board.id.value,
                    listOf(PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-01T00:00:00Z"))),
                )
            photoRepository.markCompletedBatch(photos.associate { it.id to Instant.now() })
            analysisRepository.markAnalyzing(analysis.id, Instant.now())
            deviceTokenRepository.upsert(user.id, "device-1", DevicePlatform.IOS, "fcm-token-1")

            val photoRefs =
                photos.map {
                    PhotoRef(photoId = it.id, gcsUri = "gs://bucket/${it.id}.jpg", mimeType = "image/jpeg")
                }

            When("파이프라인이 성공적으로 완료되면") {
                analysisPipelineEventListener.handle(AnalysisStartRequestedEvent(analysis.id, photoRefs))

                Then("분석 상태가 COMPLETED로 바뀌고 완료 알림 이벤트가 발행된다") {
                    analysisRepository.findById(analysis.id)!!.status shouldBe AnalysisStatus.COMPLETED

                    val completedMessages = fakePushNotifier.sentMessages.filter { it.data["type"] == "ANALYSIS_COMPLETED" }
                    completedMessages shouldHaveSize 1
                    completedMessages.single().tokens shouldBe listOf("fcm-token-1")
                }
            }

            When("파이프라인이 실패하면") {
                geminiClassifier.failureToThrow = RuntimeException("강제 실패")
                analysisPipelineEventListener.handle(AnalysisStartRequestedEvent(analysis.id, photoRefs))

                Then("분석 상태가 FAILED로 바뀌고 실패 알림 이벤트가 발행된다") {
                    analysisRepository.findById(analysis.id)!!.status shouldBe AnalysisStatus.FAILED

                    val failedMessages = fakePushNotifier.sentMessages.filter { it.data["type"] == "ANALYSIS_FAILED" }
                    failedMessages shouldHaveSize 1
                    failedMessages.single().tokens shouldBe listOf("fcm-token-1")
                }
            }
        }
    })
