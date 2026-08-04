package com.github.nexters.ppotto.sticker.application.port

import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.PhotoId
import java.time.Instant

interface RecapPhotoQueryPort {
    fun getByIds(
        analysisId: AnalysisId,
        boardId: BoardId,
        photoIds: Collection<PhotoId>,
    ): List<RecapPhotoMetadata>
}

data class RecapPhotoMetadata(
    val id: PhotoId,
    val imageUrl: String,
    val takenAt: Instant,
    val isRepresentative: Boolean,
)
