package com.github.nexters.ppotto.image.domain

import java.time.OffsetDateTime
import java.util.UUID

class Image(
    val id: UUID,
    val boardId: UUID,
    val uploadStatus: UploadStatus,
    val uploadSessionId: UUID,
    val createdAt: OffsetDateTime,
)
