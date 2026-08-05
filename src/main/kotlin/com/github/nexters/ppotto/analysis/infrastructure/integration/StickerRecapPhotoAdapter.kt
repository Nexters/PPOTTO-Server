package com.github.nexters.ppotto.analysis.infrastructure.integration

import com.github.nexters.ppotto.analysis.application.PhotoQueryService
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.sticker.application.port.RecapPhotoMetadata
import com.github.nexters.ppotto.sticker.application.port.RecapPhotoQueryPort
import org.springframework.stereotype.Component

@Component
class StickerRecapPhotoAdapter(
    private val photoQueryService: PhotoQueryService,
) : RecapPhotoQueryPort {
    override fun getByIds(
        analysisId: AnalysisId,
        boardId: BoardId,
        photoIds: Collection<PhotoId>,
    ): List<RecapPhotoMetadata> =
        photoQueryService
            .getReadablePhotos(analysisId, boardId, photoIds)
            .map { RecapPhotoMetadata(it.id, it.imageUrl, it.takenAt, it.isRepresentative) }
}
