package com.github.nexters.ppotto.board.presentation.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.nexters.ppotto.board.application.model.BoardJoinResult
import com.github.nexters.ppotto.board.domain.BoardRole
import com.github.nexters.ppotto.global.identifier.BoardId
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "보드 참여 결과")
data class BoardJoinResponse(
    @get:Schema(description = "참여한 보드 ID")
    @get:JsonProperty("boardId")
    val boardId: BoardId,

    @field:Schema(description = "현재 보드 이름", example = "제주 여행")
    val boardName: String,

    @field:Schema(description = "요청 사용자의 현재 보드 역할", enumAsRef = true)
    val role: BoardRole,
) {
    companion object {
        fun from(result: BoardJoinResult): BoardJoinResponse =
            BoardJoinResponse(
                boardId = result.boardId,
                boardName = result.boardName,
                role = result.role,
            )
    }
}
