package com.github.nexters.ppotto.analysis.infrastructure.integration

import com.github.nexters.ppotto.analysis.application.PhotoQueryService
import com.github.nexters.ppotto.analysis.domain.GeminiClassifier
import com.github.nexters.ppotto.analysis.domain.StickerGenerator
import com.github.nexters.ppotto.analysis.domain.StickerStorage
import com.github.nexters.ppotto.analysis.infrastructure.StickerObjectKeys
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.sticker.application.port.StickerRegenerationPort
import com.github.nexters.ppotto.sticker.application.port.StickerRegenerationResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class AnalysisStickerRegenerationAdapter(
    private val photoQueryService: PhotoQueryService,
    private val geminiClassifier: GeminiClassifier,
    private val stickerGenerator: StickerGenerator,
    private val stickerStorage: StickerStorage,
) : StickerRegenerationPort {
    override fun regenerate(
        analysisId: AnalysisId,
        boardId: BoardId,
        stickerId: StickerId,
        photoIds: Collection<PhotoId>,
        previousSourcePhotoId: PhotoId,
    ): StickerRegenerationResult {
        val photoRefs = photoQueryService.getPhotoRefs(analysisId, boardId, photoIds)
        warnMissingPhotos(analysisId, boardId, stickerId, photoIds, photoRefs.map { PhotoId(it.photoId) })
        if (photoRefs.isEmpty()) {
            throw InvalidInputException(message = "재생성할 수 있는 사진이 없습니다.")
        }

        val target = geminiClassifier.regenerateSticker(photoRefs, previousSourcePhotoId.value)

        val sourcePhoto =
            photoRefs.find { it.photoId == target.stickerSourcePhotoId }
                ?: error("재생성된 스티커의 원본 사진을 찾을 수 없습니다: ${target.stickerSourcePhotoId}")

        val bytes = stickerGenerator.generate(sourcePhoto.gcsUri, sourcePhoto.mimeType, target.stickerTargetSubject)
        val objectKey =
            StickerObjectKeys.keyForRegeneration(
                stickerId.value,
                sourcePhoto.photoId,
                UUID.randomUUID(),
            )
        val imageKey = stickerStorage.upload(objectKey, bytes)

        return StickerRegenerationResult(
            sourcePhotoId = PhotoId(sourcePhoto.photoId),
            imageKey = imageKey,
        )
    }

    private fun warnMissingPhotos(
        analysisId: AnalysisId,
        boardId: BoardId,
        stickerId: StickerId,
        requestedPhotoIds: Collection<PhotoId>,
        resolvedPhotoIds: Collection<PhotoId>,
    ) {
        val requested = requestedPhotoIds.toSet()
        val resolved = resolvedPhotoIds.toSet()
        val missing = requested - resolved

        if (missing.isNotEmpty()) {
            log.warn(
                "스티커 재생성 사진 일부 누락: stickerId={}, analysisId={}, boardId={}, requestedCount={}, resolvedCount={}, missingPhotoIds={}",
                stickerId,
                analysisId,
                boardId,
                requested.size,
                resolved.size,
                missing,
            )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AnalysisStickerRegenerationAdapter::class.java)
    }
}
