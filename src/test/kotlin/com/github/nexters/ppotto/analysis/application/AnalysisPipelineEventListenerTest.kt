package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.AnalysisStartRequestedEvent
import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.analysis.domain.PhotoRef
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.PhotoCreate
import com.github.nexters.ppotto.analysis.infrastructure.PhotoRepository
import com.github.nexters.ppotto.analysis.support.FakeThemeClassifier
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.notification.domain.DevicePlatform
import com.github.nexters.ppotto.notification.infrastructure.DeviceTokenRepository
import com.github.nexters.ppotto.notification.support.FakePushNotifier
import com.github.nexters.ppotto.notification.support.SentPush
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.Instant

class AnalysisPipelineEventListenerTest(
    private val analysisPipelineEventListener: AnalysisPipelineEventListener,
    private val analysisRepository: AnalysisRepository,
    private val photoRepository: PhotoRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val themeClassifier: FakeThemeClassifier,
    private val fakePushNotifier: FakePushNotifier,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        afterEach {
            themeClassifier.reset()
        }

        Given("업로드가 완료된 분석과 등록된 디바이스 토큰이 있는 상태에서") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)
            val analysis = analysisRepository.save(user.id, board.id)
            val photos =
                photoRepository.saveAll(
                    analysis.id,
                    board.id,
                    listOf(PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-01T00:00:00Z"))),
                )
            photoRepository.markCompletedBatch(photos.associate { it.id to Instant.now() })
            analysisRepository.markAnalyzing(analysis.id, Instant.now())
            deviceTokenRepository.upsert(user.id, "device-1", DevicePlatform.IOS, "fcm-token-1")

            val photoRefs =
                photos.map {
                    PhotoRef(photoId = it.id, sourceUri = "gs://bucket/${it.id}.jpg", mimeType = "image/jpeg")
                }

            When("파이프라인이 성공적으로 완료되면") {
                analysisPipelineEventListener.handle(AnalysisStartRequestedEvent(analysis.id, photoRefs))

                Then("분석 상태가 COMPLETED로 바뀐다") {
                    analysisRepository.findById(analysis.id)!!.status shouldBe AnalysisStatus.COMPLETED
                }

                Then("완료 알림이 등록된 디바이스 토큰으로 발송된다") {
                    val completedMessages = fakePushNotifier.messagesFor(analysis.id, "ANALYSIS_COMPLETED")
                    completedMessages shouldHaveSize 1
                    completedMessages.single().tokens shouldBe listOf("fcm-token-1")
                }
            }

            When("파이프라인이 실패하면") {
                themeClassifier.failureToThrow = RuntimeException("강제 실패")
                analysisPipelineEventListener.handle(AnalysisStartRequestedEvent(analysis.id, photoRefs))

                Then("분석 상태가 FAILED로 바뀐다") {
                    analysisRepository.findById(analysis.id)!!.status shouldBe AnalysisStatus.FAILED
                }

                Then("실패 알림이 등록된 디바이스 토큰으로 발송된다") {
                    val failedMessages = fakePushNotifier.messagesFor(analysis.id, "ANALYSIS_FAILED")
                    failedMessages shouldHaveSize 1
                    failedMessages.single().tokens shouldBe listOf("fcm-token-1")
                }
            }
        }
    })

private fun FakePushNotifier.messagesFor(
    analysisId: AnalysisId,
    type: String,
): List<SentPush> = sentMessages.filter { it.data["analysisId"] == analysisId.toString() && it.data["type"] == type }
