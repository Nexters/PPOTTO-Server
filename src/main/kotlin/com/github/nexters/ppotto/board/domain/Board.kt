package com.github.nexters.ppotto.board.domain

import java.time.Instant
import java.util.UUID

data class Board(
    val id: UUID,
    val userId: UUID,
    val name: String,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        const val MAX_COUNT = 100
        const val MAX_NAME_LENGTH = 10

        fun defaultName(sequence: Int): String = "Board $sequence"
    }
}
