package com.github.nexters.ppotto.sticker.domain

import com.github.nexters.ppotto.global.error.InvalidInputException
import java.time.Instant
import java.util.UUID

data class RecapComment(
    val id: UUID,
    val stickerId: UUID,
    val content: String,
    val isFloat: Boolean,
    val posX: Double?,
    val posY: Double?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class RecapCommentCreation(
    val content: String,
    val isFloat: Boolean,
    val posX: Double?,
    val posY: Double?,
) {
    init {
        content
            .takeUnless(String::isBlank)
            ?.takeIf { !isFloat || (posX != null && posY != null) }
            ?: throw InvalidInputException()
    }
}
