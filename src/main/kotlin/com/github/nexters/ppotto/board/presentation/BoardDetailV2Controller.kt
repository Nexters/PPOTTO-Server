package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.application.BoardQueryService
import com.github.nexters.ppotto.board.presentation.dto.BoardDetailV2Response
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.response.ApiResponse
import com.github.nexters.ppotto.global.security.AuthenticatedUser
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class BoardDetailV2Controller(
    private val boardQueryService: BoardQueryService,
) : BoardDetailV2Api {
    override fun get(
        @AuthenticatedUser userId: UserId,
        @PathVariable boardId: BoardId,
    ): ApiResponse<BoardDetailV2Response> =
        boardQueryService
            .getDetail(boardId, userId)
            .let(BoardDetailV2Response::from)
            .let { ApiResponse.success(it) }
}
