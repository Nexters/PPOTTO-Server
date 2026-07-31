package com.github.nexters.ppotto.sticker.application.port

import java.time.Instant
import java.util.UUID

interface RecapPhotoQueryPort {
    fun getByIds(
        analysisId: UUID,
        boardId: UUID,
        photoIds: Collection<UUID>,
    ): List<RecapPhotoMetadata>
}

data class RecapPhotoMetadata(
    val id: UUID,
    val imageUrl: String,
    val takenAt: Instant,
)
