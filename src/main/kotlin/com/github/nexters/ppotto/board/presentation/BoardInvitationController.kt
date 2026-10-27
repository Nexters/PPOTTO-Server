package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.application.BoardInvitationService
import com.github.nexters.ppotto.board.presentation.dto.BoardInvitationIssueResponse
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.security.AuthenticatedUser
import com.github.nexters.ppotto.global.web.ApiResponse
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class BoardInvitationController(
    private val boardInvitationService: BoardInvitationService,
) : BoardInvitationApi {
    override fun issue(
        @AuthenticatedUser userId: UserId,
        @PathVariable boardId: BoardId,
    ): ApiResponse<BoardInvitationIssueResponse> =
        boardInvitationService
            .issue(boardId, userId)
            .let(BoardInvitationIssueResponse::from)
            .let { ApiResponse.success(it) }
}
