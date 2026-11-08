package com.github.nexters.ppotto.board.domain

import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import java.time.Instant
import java.util.UUID

data class BoardShare(
    val id: UUID,
    val boardId: BoardId,
    val sharerUserId: UserId,
    val shareToken: String,
    val scope: BoardShareScope,
    val createdAt: Instant,
    val updatedAt: Instant,
)
