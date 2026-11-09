package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.application.BoardParticipationService
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.security.AuthenticatedUser
import com.github.nexters.ppotto.global.web.ApiResponse
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class BoardLeaveController(
    private val boardParticipationService: BoardParticipationService,
) : BoardLeaveApi {
    override fun leave(
        @AuthenticatedUser userId: UserId,
        @PathVariable boardId: BoardId,
    ): ApiResponse<Unit> {
        boardParticipationService.leave(boardId, userId)
        return ApiResponse.success()
    }
}
