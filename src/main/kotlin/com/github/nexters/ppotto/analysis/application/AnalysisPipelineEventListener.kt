package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.AnalysisStartRequestedEvent
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.Instant

@Component
class AnalysisPipelineEventListener(
    private val analysisPipelineService: AnalysisPipelineService,
    private val analysisRepository: AnalysisRepository,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: AnalysisStartRequestedEvent) {
        runCatching { analysisPipelineService.run(event.analysisId, event.photos) }
            .onSuccess {
                log.info("analysis pipeline result for analysisId={}: {}", event.analysisId, it)
                // TODO: 결과 저장 로직(테마/스티커/리캡 DB 저장) 추가 시, markCompleted 호출을
                //  저장 성공 이후로 옮길 것 — 현재는 저장 없이 COMPLETED로 표시됨 (이번 브랜치 스코프 제한)
                analysisRepository.markCompleted(event.analysisId, Instant.now())
            }.onFailure {
                log.error("analysis pipeline failed for analysisId={}", event.analysisId, it)
                val errorMessage = it.message ?: it::class.simpleName ?: "알 수 없는 오류"
                analysisRepository.markFailed(event.analysisId, errorMessage)
            }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AnalysisPipelineEventListener::class.java)
    }
}
