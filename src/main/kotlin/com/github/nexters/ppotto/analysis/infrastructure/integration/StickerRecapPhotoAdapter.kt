package com.github.nexters.ppotto.analysis.infrastructure.integration

import com.github.nexters.ppotto.analysis.application.PhotoQueryService
import com.github.nexters.ppotto.sticker.application.port.RecapPhotoMetadata
import com.github.nexters.ppotto.sticker.application.port.RecapPhotoQueryPort
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class StickerRecapPhotoAdapter(
    private val photoQueryService: PhotoQueryService,
) : RecapPhotoQueryPort {
    override fun getByIds(
        analysisId: UUID,
        boardId: UUID,
        photoIds: Collection<UUID>,
    ): List<RecapPhotoMetadata> =
        photoQueryService
            .getReadablePhotos(analysisId, boardId, photoIds)
            .map { RecapPhotoMetadata(it.id, it.imageUrl, it.takenAt) }
}
