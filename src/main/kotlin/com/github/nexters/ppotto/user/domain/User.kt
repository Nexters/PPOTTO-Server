package com.github.nexters.ppotto.user.domain

import java.time.OffsetDateTime
import java.util.UUID

class User(
    val id: UUID,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)
