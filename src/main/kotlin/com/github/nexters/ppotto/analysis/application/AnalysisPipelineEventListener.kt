package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.application.model.ThemeAnalysisResult
import com.github.nexters.ppotto.analysis.domain.Analysis
import com.github.nexters.ppotto.analysis.domain.AnalysisErrorCode
import com.github.nexters.ppotto.analysis.domain.AnalysisStartRequestedEvent
import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import com.github.nexters.ppotto.analysis.infrastructure.config.AnalysisPipelineProperties
import com.github.nexters.ppotto.analysis.infrastructure.persistence.AnalysisRepository
import com.github.nexters.ppotto.global.config.AsyncConfig
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.logging.bestEffort
import com.github.nexters.ppotto.notification.domain.PushNotificationRequestedEvent
import com.github.nexters.ppotto.sticker.application.AnalysisResultSaveService
import com.github.nexters.ppotto.sticker.application.model.AnalysisStickerResult
import com.github.nexters.ppotto.sticker.application.model.SaveAnalysisResultCommand
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
import java.util.concurrent.Semaphore

@Component
class AnalysisPipelineEventListener(
    private val analysisPipelineService: AnalysisPipelineService,
    private val analysisRepository: AnalysisRepository,
    private val analysisResultSaveService: AnalysisResultSaveService,
    private val eventPublisher: ApplicationEventPublisher,
    private val transactionTemplate: TransactionTemplate,
    transactionManager: PlatformTransactionManager,
    pipelineProperties: AnalysisPipelineProperties,
) {
    private val pipelineSlots = Semaphore(pipelineProperties.maxConcurrentRuns)

    private val progressTransactionTemplate =
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }

    @Async(AsyncConfig.ANALYSIS_PIPELINE_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Suppress("TooGenericExceptionCaught")
    fun handle(event: AnalysisStartRequestedEvent) {
        val analysisId = event.analysisId
        val pipelineRun = PipelineRun(analysisId)
        try {
            withPipelineSlot(analysisId) { runPipeline(pipelineRun, event) }
        } catch (_: Throwable) {
            if (analysisRepository.markFailed(analysisId, pipelineRun.failureReason(), pipelineRun.failedCode) > 0) {
                notifyFailureBestEffort(analysisId)
            }
        }
    }

    private fun runPipeline(
        pipelineRun: PipelineRun,
        event: AnalysisStartRequestedEvent,
    ) {
        val analysisId = event.analysisId
        val pipelineResult =
            pipelineRun.measured(PIPELINE_RUN_STEP) {
                analysisPipelineService.run(pipelineRun, event.photos) { progress ->
                    updateProgressBestEffort(analysisId, progress)
                }
            }
        val analysis =
            pipelineRun.measured(ANALYSIS_LOAD_STEP) {
                analysisRepository.findById(analysisId) ?: error("분석을 찾을 수 없습니다: $analysisId")
            }
        val stickers = pipelineResult.themes.mapNotNull { it.toStickerResult() }
        val completed =
            pipelineRun.measured(ANALYSIS_RESULT_SAVE_STEP, AnalysisErrorCode.RESULT_SAVE_FAILED) {
                saveResult(analysis, stickers)
            }
        if (completed) notifyBestEffort(analysis.userId, analysisId, NOTIFICATION_COMPLETED)
    }

    private fun <T> withPipelineSlot(
        analysisId: AnalysisId,
        block: () -> T,
    ): T {
        if (!pipelineSlots.tryAcquire()) {
            log.info("분석 파이프라인 동시 실행 상한 대기: analysisId={}, waiting={}", analysisId, pipelineSlots.queueLength)
            pipelineSlots.acquire()
        }
        return try {
            block()
        } finally {
            pipelineSlots.release()
        }
    }

    private fun saveResult(
        analysis: Analysis,
        stickers: List<AnalysisStickerResult>,
    ): Boolean =
        transactionTemplate.execute {
            val currentAnalysis = checkNotNull(analysisRepository.findByIdForUpdate(analysis.id))
            if (currentAnalysis.status != AnalysisStatus.ANALYZING) return@execute false

            analysisResultSaveService.save(
                SaveAnalysisResultCommand(
                    userId = analysis.userId,
                    analysisId = analysis.id,
                    boardId = analysis.boardId,
                    stickers = stickers,
                ),
            )
            check(analysisRepository.markCompleted(analysis.id, Instant.now()) == 1)
            true
        } == true

    private fun notifyFailureBestEffort(analysisId: AnalysisId) {
        bestEffort(log, "분석 실패 푸시 알림 발행(analysisId=$analysisId)") {
            val analysis = checkNotNull(analysisRepository.findById(analysisId)) { "분석을 찾을 수 없습니다: $analysisId" }
            publishNotification(analysis.userId, analysisId, NOTIFICATION_FAILED)
        }
    }

    private fun notifyBestEffort(
        userId: UserId,
        analysisId: AnalysisId,
        notification: PipelineNotification,
    ) {
        bestEffort(log, "분석 푸시 알림 발행(analysisId=$analysisId, type=${notification.type})") {
            publishNotification(userId, analysisId, notification)
        }
    }

    private fun publishNotification(
        userId: UserId,
        analysisId: AnalysisId,
        notification: PipelineNotification,
    ) = eventPublisher.publishEvent(
        PushNotificationRequestedEvent(
            userId = userId,
            title = notification.title,
            body = notification.body,
            data = mapOf("analysisId" to analysisId.toString(), "type" to notification.type),
        ),
    )

    private fun updateProgressBestEffort(
        analysisId: AnalysisId,
        progress: Int,
    ) {
        bestEffort(log, "분석 진행률 갱신(analysisId=$analysisId, progress=$progress)") {
            progressTransactionTemplate.executeWithoutResult { analysisRepository.updateProgress(analysisId, progress) }
        }
    }

    private fun ThemeAnalysisResult.toStickerResult(): AnalysisStickerResult? =
        stickerImageKey?.let { imageKey ->
            AnalysisStickerResult(
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
        private const val PIPELINE_RUN_STEP = "pipeline-run"
        private const val ANALYSIS_LOAD_STEP = "analysis-load"
        private const val ANALYSIS_RESULT_SAVE_STEP = "analysis-result-save"

        private val NOTIFICATION_COMPLETED =
            PipelineNotification("스티커 생성 완료", "요청하신 스티커가 모두 준비됐어요", "ANALYSIS_COMPLETED")

        private val NOTIFICATION_FAILED =
            PipelineNotification("스티커 생성 실패", "스티커 생성에 실패했어요. 다시 시도해주세요", "ANALYSIS_FAILED")

        private val log = LoggerFactory.getLogger(AnalysisPipelineEventListener::class.java)
    }
}
