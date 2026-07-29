package com.github.nexters.ppotto.analysis.domain

import java.time.Instant
import java.util.UUID

class Photo(
    val id: UUID,
    val analysisId: UUID,
    val boardId: UUID,
    val contentType: PhotoContentType,
    val uploadStatus: UploadStatus,
    val uploadedAt: Instant?,
    val takenAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
