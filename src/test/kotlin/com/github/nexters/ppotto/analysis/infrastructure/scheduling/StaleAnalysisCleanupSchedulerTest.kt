package com.github.nexters.ppotto.analysis.infrastructure.scheduling

import com.github.nexters.ppotto.analysis.application.AnalysisNotificationService
import com.github.nexters.ppotto.analysis.application.AnalysisService
import com.github.nexters.ppotto.analysis.application.StaleAnalysisCleanupService
import com.github.nexters.ppotto.analysis.application.model.CreateAnalysisCommand
import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import com.github.nexters.ppotto.analysis.infrastructure.config.StaleAnalysisCleanupProperties
import com.github.nexters.ppotto.analysis.infrastructure.persistence.AnalysisNotificationRepository
import com.github.nexters.ppotto.analysis.infrastructure.persistence.AnalysisRepository
import com.github.nexters.ppotto.analysis.support.photoUploadGroups
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.jooq.tables.references.ANALYSIS
import com.github.nexters.ppotto.notification.domain.DevicePlatform
import com.github.nexters.ppotto.notification.infrastructure.DeviceTokenRepository
import com.github.nexters.ppotto.notification.support.FakePushNotifier
import com.github.nexters.ppotto.notification.support.SentPush
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val UPDATED_AT_TRIGGER = "analysis_set_updated_at"

class StaleAnalysisCleanupSchedulerTest(
    private val cleanupService: StaleAnalysisCleanupService,
    private val analysisNotificationService: AnalysisNotificationService,
    private val analysisService: AnalysisService,
    private val analysisRepository: AnalysisRepository,
    private val analysisNotificationRepository: AnalysisNotificationRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val fakePushNotifier: FakePushNotifier,
    private val dslContext: DSLContext,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        val scheduler =
            StaleAnalysisCleanupScheduler(
                cleanupService,
                analysisNotificationService,
                StaleAnalysisCleanupProperties(enabled = true, timeoutMinutes = 60, batchSize = 100, cron = "0 0 * * * *"),
            )

        fun staleAnalysis(notificationRequested: Boolean): AnalysisId {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            deviceTokenRepository.upsert(board.userId, "device-1", DevicePlatform.IOS, "fcm-token-1")
            val analysisId =
                analysisService
                    .createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoUploadGroups(20)))
                    .analysisId
            if (notificationRequested) analysisNotificationRepository.markRequested(analysisId, Instant.now())

            dslContext.execute("ALTER TABLE analysis DISABLE TRIGGER $UPDATED_AT_TRIGGER")
            try {
                dslContext
                    .update(ANALYSIS)
                    .set(ANALYSIS.UPDATED_AT, Instant.now().minus(3, ChronoUnit.HOURS))
                    .where(ANALYSIS.ID.eq(analysisId))
                    .execute()
            } finally {
                dslContext.execute("ALTER TABLE analysis ENABLE TRIGGER $UPDATED_AT_TRIGGER")
            }
            return analysisId
        }

        Given("완료 알림을 신청한 오래 멈춘 분석이") {
            val analysisId = staleAnalysis(notificationRequested = true)

            When("만료 스케줄러가 돌면") {
                scheduler.cleanup()

                Then("FAILED로 마감되고 실패 알림을 한 번 보낸다") {
                    analysisRepository.findById(analysisId)!!.status shouldBe AnalysisStatus.FAILED
                    fakePushNotifier.messagesFor(analysisId, "ANALYSIS_FAILED") shouldHaveSize 1
                }
            }
        }

        Given("완료 알림을 신청하지 않은 오래 멈춘 분석이") {
            val analysisId = staleAnalysis(notificationRequested = false)

            When("만료 스케줄러가 돌면") {
                scheduler.cleanup()

                Then("FAILED로 마감되지만 알림은 보내지 않는다") {
                    analysisRepository.findById(analysisId)!!.status shouldBe AnalysisStatus.FAILED
                    fakePushNotifier.messagesFor(analysisId, "ANALYSIS_FAILED").shouldBeEmpty()
                }
            }
        }
    })

private fun FakePushNotifier.messagesFor(
    analysisId: AnalysisId,
    type: String,
): List<SentPush> = sentMessages.filter { it.data["analysisId"] == analysisId.toString() && it.data["type"] == type }
