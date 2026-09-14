package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.AnalysisErrorCode
import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import com.github.nexters.ppotto.analysis.infrastructure.persistence.AnalysisNotificationRepository
import com.github.nexters.ppotto.analysis.infrastructure.persistence.AnalysisRepository
import com.github.nexters.ppotto.global.error.ConflictException
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.UserId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class AnalysisNotificationService(
    private val analysisRepository: AnalysisRepository,
    private val analysisNotificationRepository: AnalysisNotificationRepository,
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
}
