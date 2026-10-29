package com.github.nexters.ppotto.board.domain

import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.BoardInvitationId
import com.github.nexters.ppotto.global.identifier.UserId
import java.time.Instant

data class BoardInvitation(
    val id: BoardInvitationId,
    val boardId: BoardId,
    val inviterUserId: UserId,
    val code: String,
    val expiresAt: Instant,
    val previewImageKey: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
