package com.github.nexters.ppotto.board.domain

import java.time.Instant
import java.util.UUID

class Board(
    val id: UUID,
    val userId: UUID,
    val createdAt: Instant,
    val updatedAt: Instant,
)
