package com.github.nexters.ppotto.sticker.domain

import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.identifier.StickerId
import java.time.Instant
import java.util.UUID

data class RecapComment(
    val id: UUID,
    val stickerId: StickerId,
    val content: String,
    val posX: Double?,
    val posY: Double?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class RecapCommentCreation(
    val content: String,
    val posX: Double?,
    val posY: Double?,
) {
    init {
        content
            .takeUnless(String::isBlank)
            ?.takeIf { (posX == null) == (posY == null) }
            ?: throw InvalidInputException()
    }
}

data class RecapCommentPosition(
    val id: UUID,
    val posX: Double,
    val posY: Double,
)
