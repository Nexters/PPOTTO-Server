package com.github.nexters.ppotto.board.presentation.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.nexters.ppotto.board.application.model.BoardParticipant
import com.github.nexters.ppotto.board.application.model.BoardParticipants
import com.github.nexters.ppotto.board.domain.BoardRole
import com.github.nexters.ppotto.global.identifier.UserId
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "보드 참여자 목록")
data class BoardParticipantsResponse(
    @field:Schema(description = "현재 참여 인원(방장 포함)", example = "2")
    val participantCount: Int,

    @field:Schema(description = "최대 참여 인원(방장 포함)", example = "8")
    val maxParticipantCount: Int,

    @field:Schema(description = "방장 우선, 이후 참여 순서의 참여자 목록")
    val participants: List<BoardParticipantResponse>,
) {
    companion object {
        fun from(participants: BoardParticipants): BoardParticipantsResponse =
            BoardParticipantsResponse(
                participantCount = participants.participantCount,
                maxParticipantCount = participants.maxParticipantCount,
                participants = participants.participants.map(BoardParticipantResponse::from),
            )
    }
}

@Schema(description = "보드 참여자")
data class BoardParticipantResponse(
    @get:Schema(description = "사용자 ID")
    @get:JsonProperty("userId")
    val userId: UserId,

    @field:Schema(description = "사용자 이름", example = "김용희")
    val name: String,

    @field:Schema(description = "보드 역할", enumAsRef = true)
    val role: BoardRole,
) {
    companion object {
        fun from(participant: BoardParticipant): BoardParticipantResponse =
            BoardParticipantResponse(
                userId = participant.userId,
                name = participant.name,
                role = participant.role,
            )
    }
}
