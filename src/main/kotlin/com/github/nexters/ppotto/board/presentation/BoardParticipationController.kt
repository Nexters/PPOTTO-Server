package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.application.BoardParticipationService
import com.github.nexters.ppotto.board.presentation.dto.BoardJoinResponse
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.security.AuthenticatedUser
import com.github.nexters.ppotto.global.web.ApiResponse
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class BoardParticipationController(
    private val boardParticipationService: BoardParticipationService,
) : BoardParticipationApi {
    override fun join(
        @AuthenticatedUser userId: UserId,
        @PathVariable code: String,
    ): ApiResponse<BoardJoinResponse> =
        boardParticipationService
            .join(code, userId)
            .let(BoardJoinResponse::from)
            .let { ApiResponse.success(it) }
}
