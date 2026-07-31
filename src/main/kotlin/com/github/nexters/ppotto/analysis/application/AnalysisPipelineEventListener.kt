package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.AnalysisStartRequestedEvent
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.sticker.application.AnalysisResultSaveService
import com.github.nexters.ppotto.sticker.application.AnalysisStickerResult
import com.github.nexters.ppotto.sticker.application.SaveAnalysisResultCommand
import com.github.nexters.ppotto.sticker.domain.StickerLayout
import com.github.nexters.ppotto.sticker.domain.StickerType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.Instant
import java.util.UUID

@Component
class AnalysisPipelineEventListener(
    private val analysisPipelineService: AnalysisPipelineService,
    private val analysisRepository: AnalysisRepository,
    private val analysisResultSaveService: AnalysisResultSaveService,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: AnalysisStartRequestedEvent) {
        val pipelineRun =
            runCatching {
                analysisPipelineService.run(event.analysisId, event.photos) { progress ->
                    analysisRepository.updateProgress(event.analysisId, progress)
                }
            }

        pipelineRun.onSuccess { pipelineResult ->
            log.info("analysis pipeline result for analysisId={}: {}", event.analysisId, pipelineResult)

            val analysis =
                analysisRepository.findById(event.analysisId)
                    ?: error("분석을 찾을 수 없습니다: ${event.analysisId}")

            val stickers = pipelineResult.toStickerResults(event.analysisId)

            if (stickers.isNotEmpty()) {
                analysisResultSaveService.save(
                    SaveAnalysisResultCommand(
                        userId = analysis.userId,
                        analysisId = event.analysisId,
                        boardId = analysis.boardId,
                        stickers = stickers,
                    ),
                )
            }

            analysisRepository.markCompleted(event.analysisId, Instant.now())
        }
        pipelineRun.onFailure {
            log.error("analysis pipeline failed for analysisId={}", event.analysisId, it)
            val errorMessage = it.message ?: it::class.simpleName ?: "알 수 없는 오류"
            analysisRepository.markFailed(event.analysisId, errorMessage)
        }
    }

    private fun AnalysisPipelineResult.toStickerResults(analysisId: UUID): List<AnalysisStickerResult> =
        themes
            .mapIndexed { themeIndex, theme -> theme.toStickerResult(analysisId, themeIndex) }
            .filterNotNull()

    private fun ThemeAnalysisResult.toStickerResult(
        analysisId: UUID,
        themeIndex: Int,
    ): AnalysisStickerResult? {
        val imageKey = stickerImageKey
        if (imageKey == null) {
            log.warn("스티커 생성 실패: analysisId={}, theme={}", analysisId, theme)
            return null
        }

        return AnalysisStickerResult(
            type = StickerType.IMAGE,
            title = badge,
            summary = text,
            sourcePhotoId = stickerSourcePhotoId,
            imageKey = imageKey,
            textContent = null,
            layout = stickerLayout(themeIndex),
            photoIds = categorizedPhotoIds,
            comments = emptyList(),
        )
    }

    private fun stickerLayout(themeIndex: Int) =
        StickerLayout(
            posX = 40.0 * themeIndex,
            posY = 40.0 * themeIndex,
            scale = 1.0,
            rotation = 0.0,
            zIndex = themeIndex,
            badgeOffsetX = 0.0,
            badgeOffsetY = 0.0,
            badgeRotation = 0.0,
        )

    companion object {
        private val log = LoggerFactory.getLogger(AnalysisPipelineEventListener::class.java)
    }
}
