package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.AnalysisErrorCode
import com.github.nexters.ppotto.analysis.domain.PhotoRef
import com.github.nexters.ppotto.analysis.domain.StickerGenerator
import com.github.nexters.ppotto.analysis.domain.StickerStorage
import com.github.nexters.ppotto.analysis.domain.StickerSubjectVerification
import com.github.nexters.ppotto.analysis.domain.ThemeClassification
import com.github.nexters.ppotto.analysis.domain.ThemeClassifier
import com.github.nexters.ppotto.analysis.infrastructure.StickerObjectKeys
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.PhotoId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

@Service
class AnalysisPipelineService(
    private val themeClassifier: ThemeClassifier,
    private val stickerGenerator: StickerGenerator,
    private val stickerStorage: StickerStorage,
    private val progressTicker: SimulatedProgressTicker = SimulatedProgressTicker(),
    private val stepTimer: PipelineStepTimer = PipelineStepTimer(),
) {
    private val activeGeminiVerifyCount = AtomicInteger(0)

    fun run(
        analysisId: AnalysisId,
        photos: List<PhotoRef>,
        onStepFailed: (String) -> Unit = {},
        onProgress: (Int) -> Unit = {},
    ): AnalysisPipelineResult {
        val pipelineStartedAt = System.nanoTime()
        log.info("analysis pipeline started: analysisId={}, photoCount={}", analysisId, photos.size)

        val photoRefById = photos.associateBy { it.photoId }
        val representativePhotos = photos.filter { it.isRepresentative }
        val classifications = expandWithBurstSiblings(classify(analysisId, representativePhotos, onProgress, onStepFailed), photos)
        onProgress(CLASSIFICATION_COMPLETED_PROGRESS)

        val themes = processThemes(analysisId, classifications, photoRefById, onProgress)

        onProgress(STICKER_COMPLETED_PROGRESS)
        log.info(
            "analysis pipeline completed: analysisId={}, themeCount={}, stickerSuccessCount={}, elapsedMs={}",
            analysisId,
            themes.size,
            themes.count { it.stickerImageKey != null },
            elapsedMs(pipelineStartedAt),
        )
        return AnalysisPipelineResult(analysisId, themes)
    }

    private fun classify(
        analysisId: AnalysisId,
        photos: List<PhotoRef>,
        onProgress: (Int) -> Unit,
        onStepFailed: (String) -> Unit,
    ): List<ThemeClassification> =
        stepTimer
            .measuredStep(analysisId, "gemini-classification", onStepFailed) {
                progressTicker.run(
                    floor = CLASSIFICATION_STARTED_PROGRESS,
                    ceiling = CLASSIFICATION_COMPLETED_PROGRESS,
                    onProgress = onProgress,
                ) {
                    themeClassifier.classifyAndRecap(photos)
                }
            }.also { log.info("analysis pipeline classification result: analysisId={}, themeCount={}", analysisId, it.size) }

    private fun expandWithBurstSiblings(
        classifications: List<ThemeClassification>,
        photos: List<PhotoRef>,
    ): List<ThemeClassification> {
        val siblingIdsByPhotoId = burstSiblingIdsByPhotoId(photos)
        return classifications.map { classification ->
            val expandedIds =
                classification.categorizedPhotoIds
                    .flatMap { photoId -> siblingIdsByPhotoId[photoId] ?: listOf(photoId) }
                    .distinct()
            classification.copy(categorizedPhotoIds = expandedIds)
        }
    }

    private fun burstSiblingIdsByPhotoId(photos: List<PhotoRef>): Map<PhotoId, List<PhotoId>> =
        photos
            .filter { it.burstGroupId != null }
            .groupBy { it.burstGroupId }
            .values
            .flatMap { burstPhotos ->
                val siblingIds = burstPhotos.map { it.photoId }
                siblingIds.map { it to siblingIds }
            }.toMap()

    private fun processThemes(
        analysisId: AnalysisId,
        classifications: List<ThemeClassification>,
        photoRefById: Map<PhotoId, PhotoRef>,
        onProgress: (Int) -> Unit,
    ): List<ThemeAnalysisResult> {
        if (classifications.isEmpty()) return emptyList()

        val themesStartedAt = System.nanoTime()
        log.info("analysis pipeline themes started: analysisId={}, themeCount={}", analysisId, classifications.size)
        val progressEmitter = ThemeProgressEmitter(analysisId, classifications.size, onProgress)
        return Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            val tasks =
                classifications.mapIndexed { themeIndex, classification ->
                    Callable { processThemeSafely(analysisId, themeIndex, classification, photoRefById, progressEmitter) }
                }
            val themes = executor.invokeAll(tasks).map { it.getOrThrow() }
            log.info(
                "analysis pipeline themes completed: analysisId={}, themeCount={}, stickerSuccessCount={}, emittedCount={}, elapsedMs={}",
                analysisId,
                themes.size,
                themes.count { it.stickerImageKey != null },
                progressEmitter.emittedCount(),
                elapsedMs(themesStartedAt),
            )
            themes
        }
    }

    private fun processThemeSafely(
        analysisId: AnalysisId,
        themeIndex: Int,
        classification: ThemeClassification,
        photoRefById: Map<PhotoId, PhotoRef>,
        progressEmitter: ThemeProgressEmitter,
    ): ThemeAnalysisResult =
        runCatching {
            processTheme(analysisId, themeIndex, classification, photoRefById) { localProgress ->
                progressEmitter.publish(themeIndex, localProgress)
            }
        }.getOrElse {
            progressEmitter.publish(themeIndex, THEME_COMPLETED_PROGRESS)
            log.warn(
                "analysis pipeline sticker theme failed, skipping sticker: " +
                    "analysisId={}, themeIndex={}, theme={}, sourcePhotoId={}, exceptionClass={}",
                analysisId,
                themeIndex,
                classification.theme,
                classification.stickerSourcePhotoId,
                it::class.simpleName,
                it,
            )
            classification.toThemeResult(classification.stickerSourcePhotoId, stickerImageKey = null, verifiedSubject = null)
        }

    private fun processTheme(
        analysisId: AnalysisId,
        themeIndex: Int,
        classification: ThemeClassification,
        photoRefById: Map<PhotoId, PhotoRef>,
        onLocalProgress: (Int) -> Unit,
    ): ThemeAnalysisResult {
        val themeStartedAt = System.nanoTime()
        val sourcePhoto = photoRefById.getValue(classification.stickerSourcePhotoId)
        log.info(
            "analysis pipeline sticker started: analysisId={}, themeIndex={}, theme={}, sourcePhotoId={}",
            analysisId,
            themeIndex,
            classification.theme,
            sourcePhoto.photoId,
        )

        val verifyStartedAt = System.nanoTime()
        val verifiedSubject =
            progressTicker.run(THEME_STARTED_PROGRESS, THEME_VERIFY_COMPLETED_PROGRESS, onLocalProgress) {
                resolvedStickerSubject(analysisId, themeIndex, classification, sourcePhoto)
            }
        val verifyElapsedMs = elapsedMs(verifyStartedAt)
        onLocalProgress(THEME_VERIFY_COMPLETED_PROGRESS)

        val cutoutStartedAt = System.nanoTime()
        val stickerImageKey =
            verifiedSubject?.let { subject ->
                progressTicker.run(THEME_VERIFY_COMPLETED_PROGRESS, THEME_COMPLETED_PROGRESS, onLocalProgress) {
                    generateAndUploadSticker(analysisId, themeIndex, classification.theme, sourcePhoto, subject.targetSubject)
                }
            }
        onLocalProgress(THEME_COMPLETED_PROGRESS)

        log.info(
            "analysis pipeline sticker theme completed: " +
                "analysisId={}, themeIndex={}, theme={}, stickerGenerated={}, verifyElapsedMs={}, " +
                "cutoutUploadElapsedMs={}, totalElapsedMs={}",
            analysisId,
            themeIndex,
            classification.theme,
            stickerImageKey != null,
            verifyElapsedMs,
            elapsedMs(cutoutStartedAt),
            elapsedMs(themeStartedAt),
        )
        return classification.toThemeResult(sourcePhoto.photoId, stickerImageKey, verifiedSubject)
    }

    private fun resolvedStickerSubject(
        analysisId: AnalysisId,
        themeIndex: Int,
        classification: ThemeClassification,
        sourcePhoto: PhotoRef,
    ): StickerSubjectVerification? {
        val verifyStartedAt = System.nanoTime()
        return runCatching {
            stepTimer.measuredGeminiCall(
                analysisId = analysisId,
                operation = "sticker-verify",
                themeIndex = themeIndex,
                theme = classification.theme,
                activeCount = activeGeminiVerifyCount,
            ) {
                themeClassifier.verifyStickerSubject(sourcePhoto, classification.stickerTargetSubject)
            }
        }.getOrElse {
            log.warn(
                "analysis pipeline sticker verification failed, falling back to unverified targetSubject: " +
                    "analysisId={}, themeIndex={}, theme={}, exceptionClass={}, elapsedMs={}",
                analysisId,
                themeIndex,
                classification.theme,
                it::class.simpleName,
                elapsedMs(verifyStartedAt),
                it,
            )
            StickerSubjectVerification(classification.stickerTargetSubject, classification.stickerMainColor)
        }
    }

    private fun generateAndUploadSticker(
        analysisId: AnalysisId,
        themeIndex: Int,
        theme: String,
        sourcePhoto: PhotoRef,
        targetSubject: String,
    ): String? {
        val stickerStartedAt = System.nanoTime()
        val objectKey = StickerObjectKeys.keyFor(analysisId, themeIndex, sourcePhoto.photoId)
        return runCatching {
            val bytes = stickerGenerator.generate(sourcePhoto.sourceUri, sourcePhoto.mimeType, targetSubject)
            stickerStorage.upload(objectKey, bytes)
            objectKey
        }.onSuccess {
            log.info(
                "analysis pipeline sticker completed: analysisId={}, themeIndex={}, imageKey={}, elapsedMs={}",
                analysisId,
                themeIndex,
                it,
                elapsedMs(stickerStartedAt),
            )
        }.getOrElse {
            log.warn(
                "analysis pipeline sticker failed: analysisId={}, themeIndex={}, theme={}, sourcePhotoId={}, errorCode={}, elapsedMs={}",
                analysisId,
                themeIndex,
                theme,
                sourcePhoto.photoId,
                AnalysisErrorCode.STICKER_BACKGROUND_REMOVAL_FAILED.code,
                elapsedMs(stickerStartedAt),
                it,
            )
            null
        }
    }

    private fun ThemeClassification.toThemeResult(
        stickerSourcePhotoId: PhotoId,
        stickerImageKey: String?,
        verifiedSubject: StickerSubjectVerification?,
    ): ThemeAnalysisResult =
        ThemeAnalysisResult(
            theme = theme,
            categorizedPhotoIds = categorizedPhotoIds,
            badge = recap.badge,
            text = recap.text,
            stickerSourcePhotoId = stickerSourcePhotoId,
            stickerImageKey = stickerImageKey,
            stickerMainColor = verifiedSubject?.mainColor ?: stickerMainColor,
            comments = comments,
        )

    companion object {
        const val CLASSIFICATION_STARTED_PROGRESS = 10
        const val CLASSIFICATION_COMPLETED_PROGRESS = 45
        const val STICKER_COMPLETED_PROGRESS = 90

        private const val THEME_STARTED_PROGRESS = 0
        private const val THEME_VERIFY_COMPLETED_PROGRESS = 25
        private const val THEME_COMPLETED_PROGRESS = 100

        private val log = LoggerFactory.getLogger(AnalysisPipelineService::class.java)

        private fun stickerProgress(
            completedThemeProgress: Int,
            totalThemeCount: Int,
        ): Int {
            if (totalThemeCount <= 0) return STICKER_COMPLETED_PROGRESS

            val progressRange = STICKER_COMPLETED_PROGRESS - CLASSIFICATION_COMPLETED_PROGRESS
            val totalThemeProgress = totalThemeCount * THEME_COMPLETED_PROGRESS
            return CLASSIFICATION_COMPLETED_PROGRESS + (progressRange * completedThemeProgress / totalThemeProgress)
        }
    }

    private class ThemeProgressEmitter(
        private val analysisId: AnalysisId,
        private val totalThemeCount: Int,
        private val onProgress: (Int) -> Unit,
    ) {
        private val localProgressByTheme = IntArray(totalThemeCount)
        private var lastEmittedProgress = CLASSIFICATION_COMPLETED_PROGRESS
        private var emittedCount = 0

        fun emittedCount(): Int = emittedCount

        @Synchronized
        fun publish(
            themeIndex: Int,
            localProgress: Int,
        ) {
            val bounded = localProgress.coerceIn(THEME_STARTED_PROGRESS, THEME_COMPLETED_PROGRESS)
            if (bounded <= localProgressByTheme[themeIndex]) return

            localProgressByTheme[themeIndex] = bounded
            val progress = stickerProgress(localProgressByTheme.sum(), totalThemeCount)
            if (progress <= lastEmittedProgress) return

            lastEmittedProgress = progress
            emittedCount += 1
            log.debug("analysis pipeline progress emitted: analysisId={}, progress={}", analysisId, progress)
            onProgress(progress)
        }
    }
}

private fun <T> Future<T>.getOrThrow(): T =
    try {
        get()
    } catch (exception: ExecutionException) {
        val cause = exception.cause ?: exception
        if (cause is RuntimeException) throw cause
        throw IllegalStateException("analysis pipeline theme task failed", cause)
    }
