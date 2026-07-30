package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.AnalysisStartRequestedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class AnalysisPipelineEventListener(
    private val analysisPipelineService: AnalysisPipelineService,
) {
    @EventListener
    fun handle(event: AnalysisStartRequestedEvent) {
        runCatching { analysisPipelineService.run(event.photos) }
            .onSuccess { log.info("analysis pipeline result for analysisId={}: {}", event.analysisId, it) }
            .onFailure { log.error("analysis pipeline failed for analysisId={}", event.analysisId, it) }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AnalysisPipelineEventListener::class.java)
    }
}
