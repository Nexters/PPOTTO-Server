package com.github.nexters.ppotto.board.domain

import java.time.OffsetDateTime
import java.util.UUID

class Board(
    val id: UUID,
    val userId: UUID,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)
