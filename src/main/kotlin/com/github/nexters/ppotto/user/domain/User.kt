package com.github.nexters.ppotto.user.domain

import java.time.Instant
import java.util.UUID

class User(
    val id: UUID,
    val createdAt: Instant,
    val updatedAt: Instant,
)
