package com.github.nexters.ppotto.board.application.model

import com.github.nexters.ppotto.global.identifier.BoardId
import java.time.Instant

data class BoardInvitationDetail(
    val code: String,
    val boardId: BoardId,
    val boardName: String,
    val inviterName: String,
    val participantCount: Int,
    val maxParticipantCount: Int,
    val stickerCount: Int,
    val previewImageUrl: String?,
    val expiresAt: Instant,
    val viewerStatus: BoardInvitationViewerStatus?,
    val participants: List<BoardParticipant>?,
)

enum class BoardInvitationViewerStatus {
    OWNER,
    MEMBER,
    NOT_JOINED,
}
