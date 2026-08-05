package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.AnalysisCanceledEvent
import com.github.nexters.ppotto.analysis.domain.PhotoStorage
import com.github.nexters.ppotto.analysis.infrastructure.PhotoObjectKeys
import com.github.nexters.ppotto.global.config.AsyncConfig
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class AnalysisCleanupEventListener(
    private val photoStorage: PhotoStorage,
) {
    @Async(AsyncConfig.ANALYSIS_CLEANUP_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: AnalysisCanceledEvent) {
        // 1회성 비동기 삭제가 실패하면 GCS에 고아 이미지가 남는다. 재시도는 없다 — 후속 작업: FAILED 분석 prefix를 스캔하는 배치 재정리 (analysis/AGENTS.md 참고).
        runCatching {
            photoStorage.deleteByPrefix(PhotoObjectKeys.prefixFor(event.analysisId))
        }.onFailure {
            log.error("failed to clean up canceled analysis photos for analysisId={}", event.analysisId, it)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AnalysisCleanupEventListener::class.java)
    }
}
