package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.application.BoardQueryService
import com.github.nexters.ppotto.board.presentation.dto.BoardResponse
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.security.AuthenticatedUser
import com.github.nexters.ppotto.global.web.ApiResponse
import org.springframework.web.bind.annotation.RestController

@RestController
class BoardListController(
    private val boardQueryService: BoardQueryService,
) : BoardListApi {
    override fun list(
        @AuthenticatedUser userId: UserId,
    ): ApiResponse<List<BoardResponse>> =
        boardQueryService
            .list(userId)
            .map(BoardResponse::from)
            .let { ApiResponse.success(it) }
}
