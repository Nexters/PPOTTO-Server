package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.AnalysisDiscardedEvent
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisCleanupRepository
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.PhotoRepository
import com.github.nexters.ppotto.global.identifier.AnalysisId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

const val MAX_STALE_CLEANUP_BATCH_SIZE = 1_000

@Service
class StaleAnalysisCleanupService(
    private val analysisRepository: AnalysisRepository,
    private val analysisCleanupRepository: AnalysisCleanupRepository,
    private val photoRepository: PhotoRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun cleanup(
        updatedBefore: Instant,
        batchSize: Int,
    ): List<AnalysisId> {
        require(batchSize in 1..MAX_STALE_CLEANUP_BATCH_SIZE) {
            "만료 배치 크기는 1 이상 $MAX_STALE_CLEANUP_BATCH_SIZE 이하여야 합니다."
        }

        val expired = mutableListOf<AnalysisId>()
        for (analysisId in analysisCleanupRepository.findStaleActiveIds(updatedBefore, batchSize)) {
            if (analysisRepository.markFailed(analysisId, AnalysisRepository.FAILED_REASON_EXPIRED) != 1) {
                continue
            }
            photoRepository.markAllFailedByAnalysisId(analysisId)
            eventPublisher.publishEvent(AnalysisDiscardedEvent(analysisId))
            expired += analysisId
        }
        return expired
    }
}
