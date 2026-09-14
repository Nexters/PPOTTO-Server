package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.application.port.PhotoStorage
import com.github.nexters.ppotto.analysis.domain.Analysis
import com.github.nexters.ppotto.analysis.domain.AnalysisStartRequestedEvent
import com.github.nexters.ppotto.analysis.domain.toRef
import com.github.nexters.ppotto.analysis.infrastructure.config.AnalysisPipelineProperties
import com.github.nexters.ppotto.analysis.infrastructure.config.StaleAnalysisCleanupProperties
import com.github.nexters.ppotto.analysis.infrastructure.persistence.AnalysisCleanupRepository
import com.github.nexters.ppotto.analysis.infrastructure.persistence.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.persistence.PhotoRepository
import com.github.nexters.ppotto.global.logging.bestEffort
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class OrphanedAnalysisResumeService(
    private val analysisRepository: AnalysisRepository,
    private val analysisCleanupRepository: AnalysisCleanupRepository,
    private val photoRepository: PhotoRepository,
    private val photoStorage: PhotoStorage,
    private val analysisNotificationService: AnalysisNotificationService,
    private val eventPublisher: ApplicationEventPublisher,
    private val pipelineProperties: AnalysisPipelineProperties,
    private val staleCleanupProperties: StaleAnalysisCleanupProperties,
) {
    @EventListener(ApplicationReadyEvent::class)
    fun resumeOrphanedAnalyses() {
        val orphaned =
            analysisCleanupRepository.findResumableAnalyzing(
                updatedAfter = Instant.now().minus(staleCleanupProperties.timeoutMinutes, ChronoUnit.MINUTES),
                limit = pipelineProperties.resumeLimit,
            )
        if (orphaned.isEmpty()) return

        log.warn("재시작으로 끊긴 분석을 재개합니다: count={}, analysisIds={}", orphaned.size, orphaned.map { it.id })
        orphaned.forEach { analysis ->
            bestEffort(log, "끊긴 분석 재개(analysisId=${analysis.id})") { resume(analysis) }
        }
    }

    private fun resume(analysis: Analysis) {
        val photos = photoRepository.findCompletedByAnalysisId(analysis.id).map { it.toRef(photoStorage.sourceUri(it)) }
        if (photos.isEmpty()) {
            if (analysisRepository.markFailed(analysis.id, AnalysisRepository.FAILED_REASON_NOT_RESUMABLE) > 0) {
                analysisNotificationService.notifyFailureIfRequested(analysis.id)
            }
            return
        }

        eventPublisher.publishEvent(AnalysisStartRequestedEvent(analysis.id, photos))
    }

    companion object {
        private val log = LoggerFactory.getLogger(OrphanedAnalysisResumeService::class.java)
    }
}
