package com.github.nexters.ppotto.image.domain

import java.time.Instant
import java.util.UUID

class Image(
    val id: UUID,
    val boardId: UUID,
    val uploadStatus: UploadStatus,
    val uploadSessionId: UUID,
    val originalFileName: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)
