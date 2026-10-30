package com.github.nexters.ppotto.board.application.model

import com.github.nexters.ppotto.board.domain.BoardRole
import com.github.nexters.ppotto.global.identifier.UserId

data class BoardParticipants(
    val participantCount: Int,
    val maxParticipantCount: Int,
    val participants: List<BoardParticipant>,
)

data class BoardParticipant(
    val userId: UserId,
    val name: String,
    val role: BoardRole,
)
