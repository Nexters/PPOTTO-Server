package com.github.nexters.ppotto.board.application.model

import com.github.nexters.ppotto.global.identifier.BoardInvitationId
import java.time.Instant

data class BoardInvitationIssueResult(
    val invitationId: BoardInvitationId,
    val code: String,
    val expiresAt: Instant,
    val previewRequired: Boolean,
)
