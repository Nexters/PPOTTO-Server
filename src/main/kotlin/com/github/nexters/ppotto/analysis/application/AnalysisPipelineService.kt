package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.GeminiClassifier
import com.github.nexters.ppotto.analysis.domain.PhotoRef
import com.github.nexters.ppotto.analysis.domain.StickerGenerator
import com.github.nexters.ppotto.analysis.domain.StickerStorage
import com.github.nexters.ppotto.analysis.domain.StickerSubjectVerification
import com.github.nexters.ppotto.analysis.domain.ThemeClassification
import com.github.nexters.ppotto.analysis.infrastructure.StickerObjectKeys
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@Service
class AnalysisPipelineService(
    private val geminiClassifier: GeminiClassifier,
    private val stickerGenerator: StickerGenerator,
    private val stickerStorage: StickerStorage,
    private val progressTicker: SimulatedProgressTicker = SimulatedProgressTicker(),
) {
    private val activeGeminiVerifyCount = AtomicInteger(0)

    fun run(
        analysisId: UUID,
        photos: List<PhotoRef>,
        onProgress: (Int) -> Unit = {},
    ): AnalysisPipelineResult {
        val pipelineStartedAt = System.nanoTime()
        log.info("analysis pipeline started: analysisId={}, photoCount={}", analysisId, photos.size)

        val photoRefById =
            measuredStep(analysisId, "photo-indexing") {
                photos.associateBy { it.photoId }
            }
        val classifications = classify(analysisId, photos, onProgress)
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
        analysisId: UUID,
        photos: List<PhotoRef>,
        onProgress: (Int) -> Unit,
    ): List<ThemeClassification> {
        val classifications =
            measuredStep(analysisId, "gemini-classification") {
                progressTicker.run(
                    floor = CLASSIFICATION_STARTED_PROGRESS,
                    ceiling = CLASSIFICATION_COMPLETED_PROGRESS,
                    onProgress = onProgress,
                ) {
                    geminiClassifier.classifyAndRecap(photos)
                }
            }
        log.info("analysis pipeline classification result: analysisId={}, themeCount={}", analysisId, classifications.size)
        return classifications
    }

    private fun processThemes(
        analysisId: UUID,
        classifications: List<ThemeClassification>,
        photoRefById: Map<UUID, PhotoRef>,
        onProgress: (Int) -> Unit,
    ): List<ThemeAnalysisResult> {
        if (classifications.isEmpty()) return emptyList()

        val themesStartedAt = System.nanoTime()
        log.info("analysis pipeline themes started: analysisId={}, themeCount={}", analysisId, classifications.size)
        val progressDispatcher = ThemeProgressDispatcher(analysisId, classifications.size, onProgress)
        progressDispatcher.start()
        val executor = Executors.newVirtualThreadPerTaskExecutor()
        return try {
            val tasks =
                classifications.mapIndexed { themeIndex, classification ->
                    Callable {
                        processThemeSafely(analysisId, themeIndex, classification, photoRefById, progressDispatcher)
                    }
                }
            executor
                .invokeAll(tasks)
                .map { it.getOrThrow() }
                .also { themes ->
                    log.info(
                        "analysis pipeline themes completed: analysisId={}, themeCount={}, stickerSuccessCount={}, elapsedMs={}",
                        analysisId,
                        themes.size,
                        themes.count { it.stickerImageKey != null },
                        elapsedMs(themesStartedAt),
                    )
                }
        } finally {
            progressDispatcher.stopAndJoin()
            executor.shutdown()
        }
    }

    private fun processThemeSafely(
        analysisId: UUID,
        themeIndex: Int,
        classification: ThemeClassification,
        photoRefById: Map<UUID, PhotoRef>,
        progressDispatcher: ThemeProgressDispatcher,
    ): ThemeAnalysisResult =
        runCatching {
            processTheme(
                analysisId = analysisId,
                themeIndex = themeIndex,
                classification = classification,
                photoRefById = photoRefById,
            ) { localProgress ->
                progressDispatcher.publish(themeIndex, localProgress)
            }
        }.getOrElse {
            progressDispatcher.publish(themeIndex, THEME_COMPLETED_PROGRESS)
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
            ThemeAnalysisResult(
                theme = classification.theme,
                categorizedPhotoIds = classification.categorizedPhotoIds,
                badge = classification.recap.badge,
                text = classification.recap.text,
                stickerTargetSubject = classification.stickerTargetSubject,
                stickerSourcePhotoId = classification.stickerSourcePhotoId,
                stickerImageKey = null,
                stickerMainColor = classification.stickerMainColor,
                comments = classification.comments,
            )
        }

    private fun processTheme(
        analysisId: UUID,
        themeIndex: Int,
        classification: ThemeClassification,
        photoRefById: Map<UUID, PhotoRef>,
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
        val verifiedSubject = verifyStickerSubjectWithTiming(analysisId, themeIndex, classification, sourcePhoto, onLocalProgress)
        onLocalProgress(THEME_VERIFY_COMPLETED_PROGRESS)
        val stickerImageKey =
            verifiedSubject.value
                ?.let { generateAndUploadStickerWithTiming(analysisId, themeIndex, classification, sourcePhoto, it, onLocalProgress) }
        onLocalProgress(THEME_COMPLETED_PROGRESS)
        log.info(
            "analysis pipeline sticker theme completed: " +
                "analysisId={}, themeIndex={}, theme={}, stickerGenerated={}, verifyElapsedMs={}, " +
                "verifyFallback={}, cutoutUploadElapsedMs={}, totalElapsedMs={}",
            analysisId,
            themeIndex,
            classification.theme,
            stickerImageKey?.value != null,
            verifiedSubject.elapsedMs,
            verifiedSubject.fallback,
            stickerImageKey?.elapsedMs,
            elapsedMs(themeStartedAt),
        )
        return ThemeAnalysisResult(
            theme = classification.theme,
            categorizedPhotoIds = classification.categorizedPhotoIds,
            badge = classification.recap.badge,
            text = classification.recap.text,
            stickerTargetSubject = verifiedSubject.value?.targetSubject ?: classification.stickerTargetSubject,
            stickerSourcePhotoId = sourcePhoto.photoId,
            stickerImageKey = stickerImageKey?.value,
            stickerMainColor = verifiedSubject.value?.mainColor ?: classification.stickerMainColor,
            comments = classification.comments,
        )
    }

    private fun verifyStickerSubjectWithTiming(
        analysisId: UUID,
        themeIndex: Int,
        classification: ThemeClassification,
        sourcePhoto: PhotoRef,
        onLocalProgress: (Int) -> Unit,
    ): TimedPipelineResult<StickerSubjectVerification?> {
        val startedAt = System.nanoTime()
        val value =
            progressTicker.run(
                floor = THEME_STARTED_PROGRESS,
                ceiling = THEME_VERIFY_COMPLETED_PROGRESS,
                onProgress = onLocalProgress,
            ) {
                resolvedStickerSubject(analysisId, themeIndex, classification, sourcePhoto)
            }
        val elapsedMs = elapsedMs(startedAt)
        log.info(
            "analysis pipeline sticker verify stage completed: " +
                "analysisId={}, themeIndex={}, theme={}, targetPresent={}, fallback={}, elapsedMs={}",
            analysisId,
            themeIndex,
            classification.theme,
            value.verification != null,
            value.fallback,
            elapsedMs,
        )
        return TimedPipelineResult(
            value = value.verification,
            elapsedMs = elapsedMs,
            fallback = value.fallback,
        )
    }

    private fun generateAndUploadStickerWithTiming(
        analysisId: UUID,
        themeIndex: Int,
        classification: ThemeClassification,
        sourcePhoto: PhotoRef,
        verifiedSubject: StickerSubjectVerification,
        onLocalProgress: (Int) -> Unit,
    ): TimedPipelineResult<String?> {
        val startedAt = System.nanoTime()
        val value =
            progressTicker.run(
                floor = THEME_VERIFY_COMPLETED_PROGRESS,
                ceiling = THEME_COMPLETED_PROGRESS,
                onProgress = onLocalProgress,
            ) {
                generateAndUploadSticker(
                    analysisId,
                    themeIndex,
                    classification.theme,
                    sourcePhoto,
                    verifiedSubject.targetSubject,
                )
            }
        return TimedPipelineResult(value, elapsedMs(startedAt))
    }

    private fun resolvedStickerSubject(
        analysisId: UUID,
        themeIndex: Int,
        classification: ThemeClassification,
        sourcePhoto: PhotoRef,
    ): StickerSubjectResolution {
        val verifyStartedAt = System.nanoTime()
        return runCatching {
            measuredStep(analysisId, "sticker-verify[$themeIndex]") {
                measuredGeminiCall(
                    analysisId = analysisId,
                    operation = "sticker-verify",
                    themeIndex = themeIndex,
                    theme = classification.theme,
                    activeCount = activeGeminiVerifyCount,
                ) {
                    geminiClassifier.verifyStickerSubject(sourcePhoto, classification.stickerTargetSubject)
                }
            }
        }.fold(
            onSuccess = { StickerSubjectResolution(it, fallback = false) },
            onFailure = {
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
                StickerSubjectResolution(
                    verification = StickerSubjectVerification(classification.stickerTargetSubject, classification.stickerMainColor),
                    fallback = true,
                )
            },
        )
    }

    private fun generateAndUploadSticker(
        analysisId: UUID,
        themeIndex: Int,
        theme: String,
        sourcePhoto: PhotoRef,
        targetSubject: String,
    ): String? {
        val stickerStartedAt = System.nanoTime()
        return runCatching {
            val bytes =
                measuredStep(analysisId, "sticker-cutout[$themeIndex]") {
                    stickerGenerator.generate(sourcePhoto.gcsUri, sourcePhoto.mimeType, targetSubject)
                }
            val objectKey = StickerObjectKeys.keyFor(analysisId, themeIndex, sourcePhoto.photoId)
            measuredStep(analysisId, "sticker-upload[$themeIndex]") {
                stickerStorage.upload(objectKey, bytes)
            }
        }.onFailure {
            log.warn(
                "analysis pipeline sticker failed: analysisId={}, themeIndex={}, theme={}, sourcePhotoId={}, elapsedMs={}",
                analysisId,
                themeIndex,
                theme,
                sourcePhoto.photoId,
                elapsedMs(stickerStartedAt),
                it,
            )
        }.onSuccess {
            log.info(
                "analysis pipeline sticker completed: analysisId={}, themeIndex={}, imageKey={}, elapsedMs={}",
                analysisId,
                themeIndex,
                it,
                elapsedMs(stickerStartedAt),
            )
        }.getOrNull()
    }

    private fun <T> measuredGeminiCall(
        analysisId: UUID,
        operation: String,
        themeIndex: Int,
        theme: String,
        activeCount: AtomicInteger,
        block: () -> T,
    ): T {
        val startedAt = System.nanoTime()
        val currentActiveCount = activeCount.incrementAndGet()
        log.info(
            "analysis pipeline gemini call started: " +
                "analysisId={}, operation={}, themeIndex={}, theme={}, activeCount={}",
            analysisId,
            operation,
            themeIndex,
            theme,
            currentActiveCount,
        )
        return try {
            block()
        } finally {
            val remainingActiveCount = activeCount.decrementAndGet()
            log.info(
                "analysis pipeline gemini call finished: " +
                    "analysisId={}, operation={}, themeIndex={}, theme={}, activeCount={}, elapsedMs={}",
                analysisId,
                operation,
                themeIndex,
                theme,
                remainingActiveCount,
                elapsedMs(startedAt),
            )
        }
    }

    private fun <T> measuredStep(
        analysisId: UUID,
        step: String,
        block: () -> T,
    ): T {
        val startedAt = System.nanoTime()
        log.info("analysis pipeline step started: analysisId={}, step={}", analysisId, step)
        return runCatching(block)
            .onSuccess {
                log.info(
                    "analysis pipeline step completed: analysisId={}, step={}, elapsedMs={}",
                    analysisId,
                    step,
                    elapsedMs(startedAt),
                )
            }.onFailure {
                log.error(
                    "analysis pipeline step failed: analysisId={}, step={}, elapsedMs={}",
                    analysisId,
                    step,
                    elapsedMs(startedAt),
                    it,
                )
            }.getOrElse {
                throw AnalysisPipelineStepException(step, it)
            }
    }

    companion object {
        const val CLASSIFICATION_STARTED_PROGRESS = 10
        const val CLASSIFICATION_COMPLETED_PROGRESS = 45
        const val STICKER_COMPLETED_PROGRESS = 90

        private const val THEME_STARTED_PROGRESS = 0
        private const val THEME_VERIFY_COMPLETED_PROGRESS = 25
        private const val THEME_COMPLETED_PROGRESS = 100
        private const val PROGRESS_QUEUE_CAPACITY = 100
        private const val PROGRESS_DISPATCHER_POLL_TIMEOUT_MS = 100L

        private val log = LoggerFactory.getLogger(AnalysisPipelineService::class.java)

        private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000

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

    private class ThemeProgressDispatcher(
        private val analysisId: UUID,
        private val totalThemeCount: Int,
        private val onProgress: (Int) -> Unit,
    ) {
        private val queue = LinkedBlockingQueue<ThemeProgressEvent>(PROGRESS_QUEUE_CAPACITY)
        private val stopped = AtomicBoolean(false)
        private val droppedCount = AtomicInteger(0)
        private val localProgressByTheme = IntArray(totalThemeCount)
        private var lastEmittedProgress = CLASSIFICATION_COMPLETED_PROGRESS
        private var emittedCount = 0
        private val thread =
            Thread.ofVirtual().unstarted {
                while (!stopped.get() || queue.isNotEmpty()) {
                    val event = queue.poll(PROGRESS_DISPATCHER_POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS) ?: continue
                    apply(event)
                }
            }

        fun start() {
            thread.start()
        }

        fun publish(
            themeIndex: Int,
            localProgress: Int,
        ) {
            val accepted =
                queue.offer(ThemeProgressEvent(themeIndex, localProgress.coerceIn(THEME_STARTED_PROGRESS, THEME_COMPLETED_PROGRESS)))
            if (!accepted) {
                droppedCount.incrementAndGet()
            }
        }

        fun stopAndJoin() {
            stopped.set(true)
            thread.join()
            log.info(
                "analysis pipeline progress dispatcher completed: " +
                    "analysisId={}, themeCount={}, emittedCount={}, droppedCount={}, lastProgress={}",
                analysisId,
                totalThemeCount,
                emittedCount,
                droppedCount.get(),
                lastEmittedProgress,
            )
        }

        private fun apply(event: ThemeProgressEvent) {
            if (event.localProgress <= localProgressByTheme[event.themeIndex]) return

            localProgressByTheme[event.themeIndex] = event.localProgress
            val progress = stickerProgress(localProgressByTheme.sum(), totalThemeCount)
            if (progress <= lastEmittedProgress) return

            lastEmittedProgress = progress
            emittedCount += 1
            onProgress(progress)
        }
    }

    private data class ThemeProgressEvent(
        val themeIndex: Int,
        val localProgress: Int,
    )

    private data class TimedPipelineResult<T>(
        val value: T,
        val elapsedMs: Long,
        val fallback: Boolean = false,
    )

    private data class StickerSubjectResolution(
        val verification: StickerSubjectVerification?,
        val fallback: Boolean,
    )
}

class AnalysisPipelineStepException(
    val step: String,
    cause: Throwable,
) : RuntimeException("$step: ${cause.message ?: cause::class.simpleName ?: "알 수 없는 오류"}", cause)

private fun <T> Future<T>.getOrThrow(): T =
    try {
        get()
    } catch (exception: ExecutionException) {
        val cause = exception.cause ?: exception
        if (cause is RuntimeException) throw cause
        throw IllegalStateException("analysis pipeline theme task failed", cause)
    }
