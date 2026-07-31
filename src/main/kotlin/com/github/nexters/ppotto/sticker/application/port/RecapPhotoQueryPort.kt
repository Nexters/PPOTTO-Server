package com.github.nexters.ppotto.sticker.application.port

import java.time.Instant
import java.util.UUID

interface RecapPhotoQueryPort {
    fun getByIds(photoIds: Collection<UUID>): List<RecapPhotoMetadata>
}

data class RecapPhotoMetadata(
    val id: UUID,
    val imageUrl: String,
    val takenAt: Instant,
)
