package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.AnalysisStartRequestedEvent
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.global.config.AsyncConfig
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.notification.domain.PushNotificationRequestedEvent
import com.github.nexters.ppotto.sticker.application.AnalysisResultSaveService
import com.github.nexters.ppotto.sticker.application.AnalysisStickerResult
import com.github.nexters.ppotto.sticker.application.SaveAnalysisResultCommand
import com.github.nexters.ppotto.sticker.domain.RecapCommentCreation
import com.github.nexters.ppotto.sticker.domain.StickerType
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

@Component
class AnalysisPipelineEventListener(
    private val analysisPipelineService: AnalysisPipelineService,
    private val analysisRepository: AnalysisRepository,
    private val analysisResultSaveService: AnalysisResultSaveService,
    private val eventPublisher: ApplicationEventPublisher,
    private val transactionTemplate: TransactionTemplate,
    transactionManager: PlatformTransactionManager,
) {
    private val stepTimer = PipelineStepTimer()

    private val progressTransactionTemplate =
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }

    @Async(AsyncConfig.ANALYSIS_PIPELINE_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: AnalysisStartRequestedEvent) {
        val startedAt = System.nanoTime()
        val analysisId = event.analysisId
        var failedStep: String? = null
        val recordFailedStep: (String) -> Unit = { step -> failedStep = failedStep ?: step }

        log.info("analysis pipeline listener started: analysisId={}, photoCount={}", analysisId, event.photos.size)
        runCatching {
            val pipelineResult =
                stepTimer.measuredStep(analysisId, "pipeline-run", recordFailedStep) {
                    analysisPipelineService.run(
                        analysisId = analysisId,
                        photos = event.photos,
                        onProgress = { progress -> updateProgressBestEffort(analysisId, progress) },
                        onStepFailed = recordFailedStep,
                    )
                }
            log.info("analysis pipeline result for analysisId={}: {}", analysisId, pipelineResult)

            val analysis =
                stepTimer.measuredStep(analysisId, "analysis-load", recordFailedStep) {
                    analysisRepository.findById(analysisId) ?: error("분석을 찾을 수 없습니다: $analysisId")
                }
            val stickers = pipelineResult.toStickerResults(analysisId)
            if (stickers.isEmpty()) {
                log.warn("analysis pipeline produced no savable stickers: analysisId={}", analysisId)
            }

            stepTimer.measuredStep(analysisId, "analysis-result-save", recordFailedStep) {
                transactionTemplate.executeWithoutResult {
                    if (stickers.isNotEmpty()) {
                        analysisResultSaveService.save(
                            SaveAnalysisResultCommand(
                                userId = analysis.userId,
                                analysisId = analysisId,
                                boardId = analysis.boardId,
                                stickers = stickers,
                            ),
                        )
                    }
                    analysisRepository.markCompleted(analysisId, Instant.now())
                }
            }
            notifyBestEffort(analysis.userId, analysisId, NOTIFICATION_COMPLETED)
        }.onSuccess {
            log.info("analysis pipeline listener completed: analysisId={}, elapsedMs={}", analysisId, elapsedMs(startedAt))
        }.onFailure { failure ->
            val step = failedStep ?: UNKNOWN_STEP
            log.error(
                "analysis pipeline listener failed: analysisId={}, step={}, elapsedMs={}",
                analysisId,
                step,
                elapsedMs(startedAt),
                failure,
            )
            analysisRepository.markFailed(analysisId, "[$step] ${failure.message ?: failure::class.simpleName ?: UNKNOWN_REASON}")
            notifyFailureBestEffort(analysisId)
        }
    }

    private fun notifyFailureBestEffort(analysisId: AnalysisId) {
        val userId = analysisRepository.findById(analysisId)?.userId
        if (userId == null) {
            log.warn("push notification publish skipped: analysisId={}, error={}", analysisId, "분석을 찾을 수 없습니다.")
            return
        }
        notifyBestEffort(userId, analysisId, NOTIFICATION_FAILED)
    }

    private fun notifyBestEffort(
        userId: UserId,
        analysisId: AnalysisId,
        notification: PipelineNotification,
    ) {
        runCatching {
            eventPublisher.publishEvent(
                PushNotificationRequestedEvent(
                    userId = userId,
                    title = notification.title,
                    body = notification.body,
                    data = mapOf("analysisId" to analysisId.toString(), "type" to notification.type),
                ),
            )
        }.onFailure {
            log.warn("push notification publish skipped: analysisId={}, error={}", analysisId, it.message ?: it::class.simpleName)
        }
    }

    private fun updateProgressBestEffort(
        analysisId: AnalysisId,
        progress: Int,
    ) {
        runCatching {
            progressTransactionTemplate.executeWithoutResult { analysisRepository.updateProgress(analysisId, progress) }
        }.onFailure {
            log.warn(
                "analysis progress update skipped: analysisId={}, progress={}, error={}",
                analysisId,
                progress,
                it.message ?: it::class.simpleName,
            )
        }
    }

    private fun AnalysisPipelineResult.toStickerResults(analysisId: AnalysisId): List<AnalysisStickerResult> =
        themes.mapNotNull { theme -> theme.toStickerResult(analysisId) }

    private fun ThemeAnalysisResult.toStickerResult(analysisId: AnalysisId): AnalysisStickerResult? {
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
            mainColor = stickerMainColor,
            photoIds = categorizedPhotoIds,
            comments = comments.map { RecapCommentCreation(content = it.content, posX = it.posX, posY = it.posY) },
        )
    }

    private data class PipelineNotification(
        val title: String,
        val body: String,
        val type: String,
    )

    companion object {
        private const val UNKNOWN_STEP = "unknown"
        private const val UNKNOWN_REASON = "알 수 없는 오류"

        private val NOTIFICATION_COMPLETED =
            PipelineNotification("스티커 생성 완료", "요청하신 스티커가 모두 준비됐어요", "ANALYSIS_COMPLETED")

        private val NOTIFICATION_FAILED =
            PipelineNotification("스티커 생성 실패", "스티커 생성에 실패했어요. 다시 시도해주세요", "ANALYSIS_FAILED")

        private val log = LoggerFactory.getLogger(AnalysisPipelineEventListener::class.java)
    }
}
