package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.application.BoardQueryService
import com.github.nexters.ppotto.board.presentation.dto.BoardListV2Response
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.security.AuthenticatedUser
import com.github.nexters.ppotto.global.web.ApiResponse
import org.springframework.web.bind.annotation.RestController

@RestController
class BoardListV2Controller(
    private val boardQueryService: BoardQueryService,
) : BoardListV2Api {
    override fun list(
        @AuthenticatedUser userId: UserId,
    ): ApiResponse<BoardListV2Response> =
        boardQueryService
            .listSummaries(userId)
            .let(BoardListV2Response::from)
            .let { ApiResponse.success(it) }
}
