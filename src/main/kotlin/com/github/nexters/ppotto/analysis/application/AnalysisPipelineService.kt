package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.PhotoRef
import com.github.nexters.ppotto.analysis.domain.StickerGenerator
import com.github.nexters.ppotto.analysis.domain.StickerStorage
import com.github.nexters.ppotto.analysis.domain.StickerSubjectVerification
import com.github.nexters.ppotto.analysis.domain.ThemeClassification
import com.github.nexters.ppotto.analysis.domain.ThemeClassifier
import com.github.nexters.ppotto.analysis.infrastructure.StickerObjectKeys
import com.github.nexters.ppotto.global.identifier.PhotoId
import org.springframework.stereotype.Service
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future

@Service
class AnalysisPipelineService(
    private val themeClassifier: ThemeClassifier,
    private val stickerGenerator: StickerGenerator,
    private val stickerStorage: StickerStorage,
    private val progressTicker: SimulatedProgressTicker = SimulatedProgressTicker(),
) {
    fun run(
        pipelineRun: PipelineRun,
        photos: List<PhotoRef>,
        onProgress: (Int) -> Unit = {},
    ): AnalysisPipelineResult {
        val photoRefById = photos.associateBy { it.photoId }
        val representativePhotos = photos.filter { it.isRepresentative }
        val classifications = expandWithBurstSiblings(classify(pipelineRun, representativePhotos, onProgress), photos)
        onProgress(CLASSIFICATION_COMPLETED_PROGRESS)

        val themes = processThemes(pipelineRun, classifications, photoRefById, onProgress)
        onProgress(STICKER_COMPLETED_PROGRESS)
        return AnalysisPipelineResult(pipelineRun.analysisId, themes)
    }

    private fun classify(
        pipelineRun: PipelineRun,
        photos: List<PhotoRef>,
        onProgress: (Int) -> Unit,
    ): List<ThemeClassification> =
        pipelineRun.measured(CLASSIFICATION_STEP) {
            progressTicker.run(
                floor = CLASSIFICATION_STARTED_PROGRESS,
                ceiling = CLASSIFICATION_COMPLETED_PROGRESS,
                onProgress = onProgress,
            ) {
                themeClassifier.classifyAndRecap(photos)
            }
        }

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
        pipelineRun: PipelineRun,
        classifications: List<ThemeClassification>,
        photoRefById: Map<PhotoId, PhotoRef>,
        onProgress: (Int) -> Unit,
    ): List<ThemeAnalysisResult> {
        if (classifications.isEmpty()) return emptyList()

        val progressEmitter = ThemeProgressEmitter(classifications.size, onProgress)
        return pipelineRun.measured(THEMES_STEP) {
            Executors.newVirtualThreadPerTaskExecutor().use { executor ->
                val tasks =
                    classifications.mapIndexed { themeIndex, classification ->
                        Callable { processTheme(pipelineRun, themeIndex, classification, photoRefById, progressEmitter) }
                    }
                executor.invokeAll(tasks).map { it.getOrThrow() }
            }
        }
    }

    private fun processTheme(
        pipelineRun: PipelineRun,
        themeIndex: Int,
        classification: ThemeClassification,
        photoRefById: Map<PhotoId, PhotoRef>,
        progressEmitter: ThemeProgressEmitter,
    ): ThemeAnalysisResult =
        pipelineRun.degrade(
            step = THEME_STEP,
            themeIndex = themeIndex,
            theme = classification.theme,
            fallback = {
                progressEmitter.publish(themeIndex, THEME_COMPLETED_PROGRESS)
                classification.toThemeResult(classification.stickerSourcePhotoId, stickerImageKey = null, verifiedSubject = null)
            },
        ) {
            val sourcePhoto = photoRefById.getValue(classification.stickerSourcePhotoId)
            val onLocalProgress: (Int) -> Unit = { localProgress -> progressEmitter.publish(themeIndex, localProgress) }

            val verifiedSubject =
                progressTicker.run(THEME_STARTED_PROGRESS, THEME_VERIFY_COMPLETED_PROGRESS, onLocalProgress) {
                    resolvedStickerSubject(pipelineRun, themeIndex, classification, sourcePhoto)
                }
            onLocalProgress(THEME_VERIFY_COMPLETED_PROGRESS)

            val stickerImageKey =
                verifiedSubject?.let { subject ->
                    progressTicker.run(THEME_VERIFY_COMPLETED_PROGRESS, THEME_COMPLETED_PROGRESS, onLocalProgress) {
                        generateAndUploadSticker(pipelineRun, themeIndex, classification.theme, sourcePhoto, subject.targetSubject)
                    }
                }
            onLocalProgress(THEME_COMPLETED_PROGRESS)
            classification.toThemeResult(sourcePhoto.photoId, stickerImageKey, verifiedSubject)
        }

    private fun resolvedStickerSubject(
        pipelineRun: PipelineRun,
        themeIndex: Int,
        classification: ThemeClassification,
        sourcePhoto: PhotoRef,
    ): StickerSubjectVerification? =
        pipelineRun.degrade(
            step = VERIFY_STEP,
            themeIndex = themeIndex,
            theme = classification.theme,
            fallback = { StickerSubjectVerification(classification.stickerTargetSubject, classification.stickerMainColor) },
        ) {
            pipelineRun.measuredGeminiCall(VERIFY_STEP, themeIndex, classification.theme) {
                themeClassifier.verifyStickerSubject(sourcePhoto, classification.stickerTargetSubject)
            }
        }

    private fun generateAndUploadSticker(
        pipelineRun: PipelineRun,
        themeIndex: Int,
        theme: String,
        sourcePhoto: PhotoRef,
        targetSubject: String,
    ): String? =
        pipelineRun.degrade(step = STICKER_STEP, themeIndex = themeIndex, theme = theme, fallback = { null }) {
            val objectKey = StickerObjectKeys.keyFor(pipelineRun.analysisId, themeIndex, sourcePhoto.photoId)
            stickerStorage.upload(objectKey, stickerGenerator.generate(sourcePhoto.sourceUri, sourcePhoto.mimeType, targetSubject))
            objectKey
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

        private const val CLASSIFICATION_STEP = "gemini-classification"
        private const val THEMES_STEP = "sticker-themes"
        private const val THEME_STEP = "sticker-theme"
        private const val VERIFY_STEP = "sticker-verify"
        private const val STICKER_STEP = "sticker-generate"

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
        private val totalThemeCount: Int,
        private val onProgress: (Int) -> Unit,
    ) {
        private val localProgressByTheme = IntArray(totalThemeCount)
        private var lastEmittedProgress = CLASSIFICATION_COMPLETED_PROGRESS

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
