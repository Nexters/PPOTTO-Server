package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.application.port.PhotoStorage
import com.github.nexters.ppotto.analysis.application.port.StickerStorage
import com.github.nexters.ppotto.analysis.domain.AnalysisDiscardedEvent
import com.github.nexters.ppotto.global.config.AsyncConfig
import com.github.nexters.ppotto.global.logging.bestEffort
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class AnalysisCleanupEventListener(
    private val photoStorage: PhotoStorage,
    private val stickerStorage: StickerStorage,
) {
    @Async(AsyncConfig.ANALYSIS_CLEANUP_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: AnalysisDiscardedEvent) {
        bestEffort(log, "버려진 분석 사진 정리(analysisId=${event.analysisId})") {
            photoStorage.deleteAll(event.analysisId)
        }
        bestEffort(log, "버려진 분석 스티커 정리(analysisId=${event.analysisId})") {
            stickerStorage.deleteAll(event.analysisId)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AnalysisCleanupEventListener::class.java)
    }
}
