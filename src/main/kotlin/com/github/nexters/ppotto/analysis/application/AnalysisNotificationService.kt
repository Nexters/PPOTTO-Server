package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.AnalysisErrorCode
import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import com.github.nexters.ppotto.analysis.infrastructure.persistence.AnalysisNotificationRepository
import com.github.nexters.ppotto.analysis.infrastructure.persistence.AnalysisRepository
import com.github.nexters.ppotto.global.error.ConflictException
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.transaction.afterCommit
import com.github.nexters.ppotto.notification.domain.PushNotificationRequestedEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class AnalysisNotificationService(
    private val analysisRepository: AnalysisRepository,
    private val analysisNotificationRepository: AnalysisNotificationRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun requestCompletionNotification(
        userId: UserId,
        analysisId: AnalysisId,
    ) {
        val analysis = analysisRepository.findByIdForUpdate(analysisId)
        if (analysis == null || analysis.userId != userId) {
            throw NotFoundException(AnalysisErrorCode.ANALYSIS_NOT_FOUND)
        }
        if (analysis.status !in AnalysisStatus.ACTIVE) {
            throw ConflictException(AnalysisErrorCode.NOTIFICATION_REQUEST_NOT_ALLOWED)
        }

        analysisNotificationRepository.markRequested(analysisId, Instant.now())
    }

    @Transactional
    fun cancelCompletionNotification(
        userId: UserId,
        analysisId: AnalysisId,
    ) {
        val analysis = analysisRepository.findByIdForUpdate(analysisId)
        if (analysis == null || analysis.userId != userId) {
            throw NotFoundException(AnalysisErrorCode.ANALYSIS_NOT_FOUND)
        }
        if (analysis.status !in AnalysisStatus.ACTIVE) {
            throw ConflictException(AnalysisErrorCode.NOTIFICATION_REQUEST_NOT_ALLOWED)
        }

        analysisNotificationRepository.clearRequested(analysisId)
    }

    fun notifyFailureIfRequested(analysisId: AnalysisId) {
        val analysis = checkNotNull(analysisRepository.findById(analysisId)) { "분석을 찾을 수 없습니다: $analysisId" }
        if (analysis.status != AnalysisStatus.FAILED || analysis.notificationRequestedAt == null) return

        afterCommit {
            eventPublisher.publishEvent(
                PushNotificationRequestedEvent(
                    userId = analysis.userId,
                    title = FAILURE_NOTIFICATION_TITLE,
                    body = FAILURE_NOTIFICATION_BODY,
                    data = mapOf("analysisId" to analysisId.toString(), "type" to FAILURE_NOTIFICATION_TYPE),
                ),
            )
        }
    }

    companion object {
        private const val FAILURE_NOTIFICATION_TYPE = "ANALYSIS_FAILED"
        private const val FAILURE_NOTIFICATION_TITLE = "스티커 생성 실패"
        private const val FAILURE_NOTIFICATION_BODY = "스티커 생성에 실패했어요. 다시 시도해주세요"
    }
}
