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

@Service
class AnalysisPipelineService(
    private val geminiClassifier: GeminiClassifier,
    private val stickerGenerator: StickerGenerator,
    private val stickerStorage: StickerStorage,
) {
    fun run(
        analysisId: UUID,
        photos: List<PhotoRef>,
        onProgress: (Int) -> Unit = {},
    ): AnalysisPipelineResult {
        val pipelineStartedAt = System.nanoTime()
        log.info("analysis pipeline started: analysisId={}, photoCount={}", analysisId, photos.size)

        val photoRefById = indexPhotos(analysisId, photos)
        val classifications = classify(analysisId, photos)
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

    private fun indexPhotos(
        analysisId: UUID,
        photos: List<PhotoRef>,
    ): Map<UUID, PhotoRef> =
        measuredStep(analysisId, "photo-indexing") {
            photos.associateBy { it.photoId }
        }

    private fun classify(
        analysisId: UUID,
        photos: List<PhotoRef>,
    ): List<ThemeClassification> {
        val classifications =
            measuredStep(analysisId, "gemini-classification") {
                geminiClassifier.classifyAndRecap(photos)
            }
        log.info("analysis pipeline classification result: analysisId={}, themeCount={}", analysisId, classifications.size)
        return classifications
    }

    private fun processThemes(
        analysisId: UUID,
        classifications: List<ThemeClassification>,
        photoRefById: Map<UUID, PhotoRef>,
        onProgress: (Int) -> Unit,
    ): List<ThemeAnalysisResult> =
        classifications.mapIndexed { themeIndex, classification ->
            val result = processTheme(analysisId, themeIndex, classification, photoRefById)
            onProgress(stickerProgress(themeIndex + 1, classifications.size))
            result
        }

    private fun processTheme(
        analysisId: UUID,
        themeIndex: Int,
        classification: ThemeClassification,
        photoRefById: Map<UUID, PhotoRef>,
    ): ThemeAnalysisResult {
        val sourcePhoto = photoRefById.getValue(classification.stickerSourcePhotoId)
        log.info(
            "analysis pipeline sticker started: analysisId={}, themeIndex={}, theme={}, sourcePhotoId={}",
            analysisId,
            themeIndex,
            classification.theme,
            sourcePhoto.photoId,
        )
        val verifiedSubject = resolvedStickerSubject(analysisId, themeIndex, classification, sourcePhoto)
        val stickerImageKey =
            verifiedSubject?.let {
                generateAndUploadSticker(analysisId, themeIndex, classification.theme, sourcePhoto, it.targetSubject)
            }
        return ThemeAnalysisResult(
            theme = classification.theme,
            categorizedPhotoIds = classification.categorizedPhotoIds,
            badge = classification.recap.badge,
            text = classification.recap.text,
            stickerTargetSubject = verifiedSubject?.targetSubject ?: classification.stickerTargetSubject,
            stickerSourcePhotoId = sourcePhoto.photoId,
            stickerImageKey = stickerImageKey,
            stickerMainColor = verifiedSubject?.mainColor ?: classification.stickerMainColor,
            comments = classification.comments,
        )
    }

    private fun resolvedStickerSubject(
        analysisId: UUID,
        themeIndex: Int,
        classification: ThemeClassification,
        sourcePhoto: PhotoRef,
    ): StickerSubjectVerification? {
        val verifyStartedAt = System.nanoTime()
        return runCatching {
            measuredStep(analysisId, "sticker-verify[$themeIndex]") {
                geminiClassifier.verifyStickerSubject(sourcePhoto, classification.stickerTargetSubject)
            }
        }.fold(
            onSuccess = { it },
            onFailure = {
                log.warn(
                    "analysis pipeline sticker verification failed, falling back to unverified targetSubject: " +
                        "analysisId={}, themeIndex={}, theme={}, elapsedMs={}",
                    analysisId,
                    themeIndex,
                    classification.theme,
                    elapsedMs(verifyStartedAt),
                    it,
                )
                StickerSubjectVerification(classification.stickerTargetSubject, classification.stickerMainColor)
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
                measuredStep(analysisId, "sticker-generate[$themeIndex]") {
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

    private fun stickerProgress(
        completedCount: Int,
        totalCount: Int,
    ): Int {
        if (totalCount <= 0) return STICKER_COMPLETED_PROGRESS

        val progressRange = STICKER_COMPLETED_PROGRESS - CLASSIFICATION_COMPLETED_PROGRESS
        return CLASSIFICATION_COMPLETED_PROGRESS + (progressRange * completedCount / totalCount)
    }

    companion object {
        const val CLASSIFICATION_COMPLETED_PROGRESS = 45
        const val STICKER_COMPLETED_PROGRESS = 90

        private val log = LoggerFactory.getLogger(AnalysisPipelineService::class.java)

        private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000
    }
}

class AnalysisPipelineStepException(
    val step: String,
    cause: Throwable,
) : RuntimeException("$step: ${cause.message ?: cause::class.simpleName ?: "알 수 없는 오류"}", cause)
