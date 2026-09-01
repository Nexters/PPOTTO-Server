package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.application.BoardQueryService
import com.github.nexters.ppotto.board.presentation.dto.BoardDetailResponse
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.response.ApiResponse
import com.github.nexters.ppotto.global.security.AuthenticatedUser
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class BoardDetailController(
    private val boardQueryService: BoardQueryService,
) : BoardDetailApi {
    override fun get(
        @AuthenticatedUser userId: UserId,
        @PathVariable boardId: BoardId,
    ): ApiResponse<BoardDetailResponse> =
        boardQueryService
            .getDetail(boardId, userId)
            .let(BoardDetailResponse::from)
            .let { ApiResponse.success(it) }
}
