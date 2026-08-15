package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.AnalysisStartRequestedEvent
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.global.config.AsyncConfig
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.sticker.application.AnalysisResultSaveService
import com.github.nexters.ppotto.sticker.application.AnalysisStickerResult
import com.github.nexters.ppotto.sticker.application.SaveAnalysisResultCommand
import com.github.nexters.ppotto.sticker.domain.RecapCommentCreation
import com.github.nexters.ppotto.sticker.domain.StickerType
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
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
    @Async(AsyncConfig.ANALYSIS_PIPELINE_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: AnalysisStartRequestedEvent) {
        val startedAt = System.nanoTime()
        log.info("analysis pipeline listener started: analysisId={}, photoCount={}", event.analysisId, event.photos.size)
        runCatching {
            val pipelineResult =
                measuredStep(event.analysisId, "pipeline-run") {
                    analysisPipelineService.run(event.analysisId, event.photos) { progress ->
                        updateProgressBestEffort(event.analysisId, progress)
                    }
                }

            log.info("analysis pipeline result for analysisId={}: {}", event.analysisId, pipelineResult)

            val analysis =
                measuredStep(event.analysisId, "analysis-load") {
                    analysisRepository.findById(event.analysisId)
                        ?: error("분석을 찾을 수 없습니다: ${event.analysisId}")
                }

            val stickers =
                measuredStep(event.analysisId, "sticker-result-mapping") {
                    pipelineResult.toStickerResults(event.analysisId)
                }

            if (stickers.isNotEmpty()) {
                measuredStep(event.analysisId, "analysis-result-save") {
                    analysisResultSaveService.save(
                        SaveAnalysisResultCommand(
                            userId = UserId(analysis.userId),
                            analysisId = AnalysisId(event.analysisId),
                            boardId = BoardId(analysis.boardId),
                            stickers = stickers,
                        ),
                    )
                }
            } else {
                log.warn("analysis pipeline produced no savable stickers: analysisId={}", event.analysisId)
            }

            measuredStep(event.analysisId, "analysis-mark-completed") {
                analysisRepository.markCompleted(event.analysisId, Instant.now())
            }
        }.onSuccess {
            log.info("analysis pipeline listener completed: analysisId={}, elapsedMs={}", event.analysisId, elapsedMs(startedAt))
        }.onFailure {
            val step = (it as? AnalysisPipelineStepException)?.step ?: "unknown"
            log.error(
                "analysis pipeline listener failed: analysisId={}, step={}, elapsedMs={}",
                event.analysisId,
                step,
                elapsedMs(startedAt),
                it,
            )
            val errorMessage = "[$step] ${it.message ?: it::class.simpleName ?: "알 수 없는 오류"}"
            analysisRepository.markFailed(event.analysisId, errorMessage)
        }
    }

    private fun <T> measuredStep(
        analysisId: UUID,
        step: String,
        block: () -> T,
    ): T {
        val startedAt = System.nanoTime()
        log.info("analysis pipeline listener step started: analysisId={}, step={}", analysisId, step)
        return runCatching(block)
            .onSuccess {
                log.info(
                    "analysis pipeline listener step completed: analysisId={}, step={}, elapsedMs={}",
                    analysisId,
                    step,
                    elapsedMs(startedAt),
                )
            }.onFailure {
                log.error(
                    "analysis pipeline listener step failed: analysisId={}, step={}, elapsedMs={}",
                    analysisId,
                    step,
                    elapsedMs(startedAt),
                    it,
                )
            }.getOrElse {
                if (it is AnalysisPipelineStepException) throw it
                throw AnalysisPipelineStepException(step, it)
            }
    }

    private fun updateProgressBestEffort(
        analysisId: UUID,
        progress: Int,
    ) {
        runCatching {
            analysisRepository.updateProgress(analysisId, progress)
        }.onFailure {
            log.warn(
                "analysis progress update skipped: analysisId={}, progress={}, error={}",
                analysisId,
                progress,
                it.message ?: it::class.simpleName,
            )
        }
    }

    private fun AnalysisPipelineResult.toStickerResults(analysisId: UUID): List<AnalysisStickerResult> =
        themes
            .map { theme -> theme.toStickerResult(analysisId) }
            .filterNotNull()

    private fun ThemeAnalysisResult.toStickerResult(analysisId: UUID): AnalysisStickerResult? {
        val imageKey = stickerImageKey
        if (imageKey == null) {
            log.warn("스티커 생성 실패: analysisId={}, theme={}", analysisId, theme)
            return null
        }

        return AnalysisStickerResult(
            type = StickerType.IMAGE,
            title = badge,
            summary = text,
            sourcePhotoId = PhotoId(stickerSourcePhotoId),
            imageKey = imageKey,
            textContent = null,
            mainColor = stickerMainColor,
            photoIds = categorizedPhotoIds.map(::PhotoId),
            comments = comments.map { RecapCommentCreation(content = it.content, posX = it.posX, posY = it.posY) },
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(AnalysisPipelineEventListener::class.java)

        private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000
    }
}
