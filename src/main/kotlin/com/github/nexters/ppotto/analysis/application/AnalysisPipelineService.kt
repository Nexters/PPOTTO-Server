package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.GeminiClassifier
import com.github.nexters.ppotto.analysis.domain.PhotoRef
import com.github.nexters.ppotto.analysis.domain.StickerGenerator
import com.github.nexters.ppotto.analysis.domain.StickerStorage
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
        val photoRefById = photos.associateBy { it.photoId }
        val classifications = geminiClassifier.classifyAndRecap(photos)
        onProgress(CLASSIFICATION_COMPLETED_PROGRESS)

        val themes =
            classifications.mapIndexed { themeIndex, classification ->
                val sourcePhoto = photoRefById.getValue(classification.stickerSourcePhotoId)
                val stickerImageKey =
                    runCatching {
                        val bytes =
                            stickerGenerator.generate(
                                sourcePhoto.gcsUri,
                                sourcePhoto.mimeType,
                                classification.stickerTargetSubject,
                            )
                        val objectKey = StickerObjectKeys.keyFor(analysisId, themeIndex, sourcePhoto.photoId)
                        stickerStorage.upload(objectKey, bytes)
                    }.onFailure {
                        log.warn(
                            "스티커 생성 실패: analysisId={}, theme={}, sourcePhotoId={}",
                            analysisId,
                            classification.theme,
                            sourcePhoto.photoId,
                            it,
                        )
                    }.getOrNull()

                onProgress(stickerProgress(themeIndex + 1, classifications.size))

                ThemeAnalysisResult(
                    theme = classification.theme,
                    categorizedPhotoIds = classification.categorizedPhotoIds,
                    badge = classification.recap.badge,
                    text = classification.recap.text,
                    stickerTargetSubject = classification.stickerTargetSubject,
                    stickerSourcePhotoId = sourcePhoto.photoId,
                    stickerImageKey = stickerImageKey,
                )
            }

        onProgress(STICKER_COMPLETED_PROGRESS)
        return AnalysisPipelineResult(analysisId, themes)
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
    }
}
