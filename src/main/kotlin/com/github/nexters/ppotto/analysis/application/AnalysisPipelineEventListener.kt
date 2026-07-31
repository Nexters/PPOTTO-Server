package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.AnalysisStartRequestedEvent
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.sticker.application.AnalysisResultSaveService
import com.github.nexters.ppotto.sticker.application.AnalysisStickerResult
import com.github.nexters.ppotto.sticker.application.SaveAnalysisResultCommand
import com.github.nexters.ppotto.sticker.domain.RecapCommentCreation
import com.github.nexters.ppotto.sticker.domain.StickerLayout
import com.github.nexters.ppotto.sticker.domain.StickerType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.Instant

@Component
class AnalysisPipelineEventListener(
    private val analysisPipelineService: AnalysisPipelineService,
    private val analysisRepository: AnalysisRepository,
    private val analysisResultSaveService: AnalysisResultSaveService,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: AnalysisStartRequestedEvent) {
        runCatching { analysisPipelineService.run(event.analysisId, event.photos) }
            .onSuccess { pipelineResult ->
                log.info("analysis pipeline result for analysisId={}: {}", event.analysisId, pipelineResult)

                val analysis = analysisRepository.findById(event.analysisId)
                    ?: error("분석을 찾을 수 없습니다: ${event.analysisId}")

                val stickers = pipelineResult.themes.mapIndexed { themeIndex, theme ->
                    if (theme.stickerImageKey == null) {
                        log.warn("스티커 생성 실패: analysisId={}, theme={}", event.analysisId, theme.theme)
                        null
                    } else {
                        AnalysisStickerResult(
                            type = StickerType.IMAGE,
                            title = theme.badge,
                            sourcePhotoId = theme.stickerSourcePhotoId,
                            imageKey = theme.stickerImageKey,
                            textContent = null,
                            layout = StickerLayout(
                                posX = 40.0 * themeIndex,
                                posY = 40.0 * themeIndex,
                                scale = 1.0,
                                rotation = 0.0,
                                zIndex = themeIndex,
                                badgeOffsetX = 0.0,
                                badgeOffsetY = 0.0,
                                badgeRotation = 0.0,
                            ),
                            photoIds = theme.categorizedPhotoIds,
                            comments = listOf(
                                RecapCommentCreation(
                                    content = theme.text,
                                    isFloat = false,
                                    posX = null,
                                    posY = null,
                                )
                            ),
                        )
                    }
                }.filterNotNull()

                if (stickers.isNotEmpty()) {
                    analysisResultSaveService.save(
                        SaveAnalysisResultCommand(
                            userId = analysis.userId,
                            analysisId = event.analysisId,
                            boardId = analysis.boardId,
                            stickers = stickers,
                        )
                    )
                }

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
