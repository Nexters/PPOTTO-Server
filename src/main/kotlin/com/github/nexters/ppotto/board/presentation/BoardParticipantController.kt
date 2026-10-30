package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.application.BoardParticipantQueryService
import com.github.nexters.ppotto.board.presentation.dto.BoardParticipantsResponse
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.security.AuthenticatedUser
import com.github.nexters.ppotto.global.web.ApiResponse
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class BoardParticipantController(
    private val boardParticipantQueryService: BoardParticipantQueryService,
) : BoardParticipantApi {
    override fun get(
        @AuthenticatedUser userId: UserId,
        @PathVariable boardId: BoardId,
    ): ApiResponse<BoardParticipantsResponse> =
        boardParticipantQueryService
            .get(boardId, userId)
            .let(BoardParticipantsResponse::from)
            .let { ApiResponse.success(it) }
}
