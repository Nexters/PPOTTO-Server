package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.AnalysisDiscardedEvent
import com.github.nexters.ppotto.analysis.domain.AnalysisErrorCode
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.PhotoRepository
import com.github.nexters.ppotto.global.identifier.AnalysisId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class AnalysisDiscardService(
    private val analysisRepository: AnalysisRepository,
    private val photoRepository: PhotoRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional(propagation = Propagation.MANDATORY)
    fun discard(
        analysisId: AnalysisId,
        failedReason: String,
        failedCode: AnalysisErrorCode,
    ): Boolean {
        if (analysisRepository.markFailed(analysisId, failedReason, failedCode) != 1) {
            return false
        }
        photoRepository.markAllFailedByAnalysisId(analysisId)
        eventPublisher.publishEvent(AnalysisDiscardedEvent(analysisId))
        return true
    }
}
