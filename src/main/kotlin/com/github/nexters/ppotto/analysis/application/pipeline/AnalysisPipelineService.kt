package com.github.nexters.ppotto.analysis.application.pipeline

import com.github.nexters.ppotto.analysis.application.model.AnalysisPipelineResult
import com.github.nexters.ppotto.analysis.application.model.ThemeAnalysisResult
import com.github.nexters.ppotto.analysis.application.port.StickerGenerator
import com.github.nexters.ppotto.analysis.application.port.StickerStorage
import com.github.nexters.ppotto.analysis.application.port.ThemeClassifier
import com.github.nexters.ppotto.analysis.domain.AnalysisErrorCode
import com.github.nexters.ppotto.analysis.domain.PhotoRef
import com.github.nexters.ppotto.analysis.domain.StickerSubjectVerification
import com.github.nexters.ppotto.analysis.domain.ThemeClassification
import com.github.nexters.ppotto.analysis.infrastructure.storage.StickerObjectKeys
import com.github.nexters.ppotto.global.error.BusinessException
import com.github.nexters.ppotto.global.identifier.PhotoId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
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
        isActive: () -> Boolean = { true },
        onProgress: (Int) -> Unit = {},
    ): AnalysisPipelineResult {
        val photoRefById = photos.associateBy { it.photoId }
        val representativePhotos = photos.filter { it.isRepresentative }
        val classifications = expandWithBurstSiblings(classify(pipelineRun, representativePhotos, onProgress), photos)
        ensureActive(isActive)
        onProgress(CLASSIFICATION_COMPLETED_PROGRESS)

        val themes = processThemes(pipelineRun, classifications, photoRefById, onProgress, isActive)
        if (themes.none { it.stickerImageKey != null }) {
            val failedCode =
                if (themes.isNotEmpty() && themes.all { it.failedCode == AnalysisErrorCode.NO_STICKER_SUBJECT }) {
                    AnalysisErrorCode.NO_STICKER_SUBJECT
                } else {
                    AnalysisErrorCode.STICKER_GENERATION_FAILED
                }
            throw BusinessException(failedCode)
        }
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
                themeClassifier
                    .classifyAndRecap(photos)
                    .also { if (it.isEmpty()) throw BusinessException(AnalysisErrorCode.INVALID_GEMINI_RESPONSE) }
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
        isActive: () -> Boolean,
    ): List<ThemeAnalysisResult> {
        if (classifications.isEmpty()) return emptyList()

        val progressEmitter = ThemeProgressEmitter(classifications.size, onProgress)
        return pipelineRun.measured(THEMES_STEP) {
            Executors.newVirtualThreadPerTaskExecutor().use { executor ->
                val tasks =
                    classifications.mapIndexed { themeIndex, classification ->
                        val context = ThemeContext(pipelineRun, themeIndex, classification, photoRefById)
                        Callable {
                            ensureActive(isActive)
                            processTheme(context, executor, progressEmitter, isActive)
                        }
                    }
                executor.invokeAll(tasks).map { it.getOrThrow() }
            }
        }
    }

    private fun processTheme(
        context: ThemeContext,
        executor: ExecutorService,
        progressEmitter: ThemeProgressEmitter,
        isActive: () -> Boolean,
    ): ThemeAnalysisResult =
        context.degrade(
            step = THEME_STEP,
            fallback = {
                progressEmitter.publish(context.themeIndex, THEME_COMPLETED_PROGRESS)
                context.classification.toThemeResult(
                    context.classification.stickerSourcePhotoId,
                    stickerImageKey = null,
                    verifiedSubject = null,
                    failedCode = AnalysisErrorCode.STICKER_GENERATION_FAILED,
                )
            },
        ) {
            val sourcePhoto = context.sourcePhoto
            val onLocalProgress: (Int) -> Unit = { localProgress -> progressEmitter.publish(context.themeIndex, localProgress) }

            val (verifiedSubject, stickerImage) =
                progressTicker.run(THEME_STARTED_PROGRESS, THEME_COMPLETED_PROGRESS, onLocalProgress) {
                    val verification: Future<StickerSubjectVerification?> =
                        executor.submit(Callable { resolvedStickerSubject(context, sourcePhoto) })
                    val image = generateStickerImage(context, sourcePhoto)
                    verification.getOrThrow() to image
                }
            onLocalProgress(THEME_COMPLETED_PROGRESS)

            val stickerImageKey = uploadedStickerKey(context, sourcePhoto, stickerImage, verifiedSubject, isActive)
            context.classification.toThemeResult(
                sourcePhoto.photoId,
                stickerImageKey,
                verifiedSubject,
                failedCode =
                    when {
                        verifiedSubject == null -> AnalysisErrorCode.NO_STICKER_SUBJECT
                        stickerImageKey == null -> AnalysisErrorCode.STICKER_GENERATION_FAILED
                        else -> null
                    },
            )
        }

    private fun resolvedStickerSubject(
        context: ThemeContext,
        sourcePhoto: PhotoRef,
    ): StickerSubjectVerification? =
        context.degrade(
            step = VERIFY_STEP,
            fallback = { StickerSubjectVerification(context.classification.stickerTargetSubject, context.classification.stickerMainColor) },
        ) {
            context.pipelineRun.measuredGeminiCall(VERIFY_STEP, context.themeIndex, context.theme) {
                themeClassifier.verifyStickerSubject(sourcePhoto, context.classification.stickerTargetSubject)
            }
        }

    private fun generateStickerImage(
        context: ThemeContext,
        sourcePhoto: PhotoRef,
    ): ByteArray? =
        context.degrade(step = STICKER_STEP, fallback = { null }) {
            stickerGenerator.generate(sourcePhoto.sourceUri, sourcePhoto.mimeType, context.classification.stickerTargetSubject)
        }

    private fun uploadedStickerKey(
        context: ThemeContext,
        sourcePhoto: PhotoRef,
        stickerImage: ByteArray?,
        verifiedSubject: StickerSubjectVerification?,
        isActive: () -> Boolean,
    ): String? {
        if (stickerImage == null) return null
        if (verifiedSubject == null) {
            log.warn(
                "재확인이 피사체 없음으로 판정해 이미 만든 스티커를 버립니다: analysisId={}, themeIndex={}, theme={}",
                context.pipelineRun.analysisId,
                context.themeIndex,
                context.theme,
            )
            return null
        }

        ensureActive(isActive)
        return context.degrade(step = STICKER_UPLOAD_STEP, fallback = { null }) {
            val objectKey = StickerObjectKeys.keyFor(context.pipelineRun.analysisId, context.themeIndex, sourcePhoto.photoId)
            stickerStorage.upload(objectKey, stickerImage)
            objectKey
        }
    }

    private class ThemeContext(
        val pipelineRun: PipelineRun,
        val themeIndex: Int,
        val classification: ThemeClassification,
        private val photoRefById: Map<PhotoId, PhotoRef>,
    ) {
        val theme: String get() = classification.theme

        val sourcePhoto: PhotoRef get() = photoRefById.getValue(classification.stickerSourcePhotoId)

        fun <T> degrade(
            step: String,
            fallback: () -> T,
            block: () -> T,
        ): T = pipelineRun.degrade(step, themeIndex, theme, fallback, block)
    }

    private fun ThemeClassification.toThemeResult(
        stickerSourcePhotoId: PhotoId,
        stickerImageKey: String?,
        verifiedSubject: StickerSubjectVerification?,
        failedCode: AnalysisErrorCode?,
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
            failedCode = failedCode,
        )

    companion object {
        const val CLASSIFICATION_STARTED_PROGRESS = 10
        const val CLASSIFICATION_COMPLETED_PROGRESS = 45
        const val STICKER_COMPLETED_PROGRESS = 90

        private const val THEME_STARTED_PROGRESS = 0
        private const val THEME_COMPLETED_PROGRESS = 100

        private const val CLASSIFICATION_STEP = "gemini-classification"
        private const val THEMES_STEP = "sticker-themes"
        private const val THEME_STEP = "sticker-theme"
        private const val VERIFY_STEP = "sticker-verify"
        private const val STICKER_STEP = "sticker-generate"
        private const val STICKER_UPLOAD_STEP = "sticker-upload"

        private val log = LoggerFactory.getLogger(AnalysisPipelineService::class.java)

        private fun ensureActive(isActive: () -> Boolean) {
            if (!isActive()) throw AnalysisPipelineCanceledException()
        }

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
