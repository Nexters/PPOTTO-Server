package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.GeminiClassifier
import com.github.nexters.ppotto.analysis.domain.PhotoRef
import com.github.nexters.ppotto.analysis.domain.StickerGenerator
import com.github.nexters.ppotto.analysis.domain.StickerObjectKeys
import com.github.nexters.ppotto.analysis.domain.StickerStorage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AnalysisPipelineService(
    private val geminiClassifier: GeminiClassifier,
    private val stickerGenerator: StickerGenerator,
    private val stickerStorage: StickerStorage,
    private val stickerObjectKeys: StickerObjectKeys,
) {
    fun run(photos: List<PhotoRef>): AnalysisPipelineResult {
        val pipelineRunId = UUID.randomUUID()
        val photoRefById = photos.associateBy { it.photoId }
        val classifications = geminiClassifier.classifyAndRecap(photos)

        val themes =
            classifications.map { classification ->
                val sourcePhoto = photoRefById.getValue(classification.stickerSourcePhotoId)
                val stickerUrl =
                    runCatching {
                        val bytes =
                            stickerGenerator.generate(
                                sourcePhoto.gcsUri,
                                sourcePhoto.mimeType,
                                classification.stickerTargetSubject,
                            )
                        val objectKey = stickerObjectKeys.keyFor(pipelineRunId, classification.theme, sourcePhoto.photoId)
                        stickerStorage.upload(objectKey, bytes)
                    }.onFailure {
                        log.warn("스티커 생성 실패: theme={}, sourcePhotoId={}", classification.theme, sourcePhoto.photoId, it)
                    }.getOrNull()

                ThemeAnalysisResult(
                    theme = classification.theme,
                    categorizedPhotoIds = classification.categorizedPhotoIds,
                    badge = classification.recap.badge,
                    text = classification.recap.text,
                    stickerTargetSubject = classification.stickerTargetSubject,
                    stickerSourcePhotoId = sourcePhoto.photoId,
                    stickerUrl = stickerUrl,
                )
            }

        return AnalysisPipelineResult(pipelineRunId, themes)
    }

    companion object {
        private val log = LoggerFactory.getLogger(AnalysisPipelineService::class.java)
    }
}
