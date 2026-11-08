package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.application.BoardShareQueryService
import com.github.nexters.ppotto.board.presentation.dto.SharedBoardResponse
import com.github.nexters.ppotto.global.web.ApiResponse
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class BoardShareQueryController(
    private val boardShareQueryService: BoardShareQueryService,
) : BoardShareQueryApi {
    override fun get(
        @PathVariable shareToken: String,
    ): ApiResponse<SharedBoardResponse> =
        boardShareQueryService
            .getSharedBoard(shareToken)
            .let(SharedBoardResponse::from)
            .let { ApiResponse.success(it) }
}
