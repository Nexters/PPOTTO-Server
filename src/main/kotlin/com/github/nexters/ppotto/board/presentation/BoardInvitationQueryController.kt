package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.application.BoardInvitationQueryService
import com.github.nexters.ppotto.board.presentation.dto.BoardInvitationDetailResponse
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.security.CurrentUser
import com.github.nexters.ppotto.global.web.ApiResponse
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class BoardInvitationQueryController(
    private val boardInvitationQueryService: BoardInvitationQueryService,
) : BoardInvitationQueryApi {
    override fun get(
        @CurrentUser userId: UserId?,
        @PathVariable code: String,
    ): ApiResponse<BoardInvitationDetailResponse> =
        boardInvitationQueryService
            .get(code, userId)
            .let(BoardInvitationDetailResponse::from)
            .let { ApiResponse.success(it) }
}
