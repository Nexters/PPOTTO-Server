package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.application.BoardShareService
import com.github.nexters.ppotto.board.presentation.dto.BoardShareRequest
import com.github.nexters.ppotto.board.presentation.dto.BoardShareResponse
import com.github.nexters.ppotto.board.presentation.dto.BoardShareStatusResponse
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.security.AuthenticatedUser
import com.github.nexters.ppotto.global.web.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class BoardShareController(
    private val boardShareService: BoardShareService,
) : BoardShareApi {
    override fun getStatus(
        @AuthenticatedUser userId: UserId,
        @PathVariable boardId: BoardId,
    ): ApiResponse<BoardShareStatusResponse> =
        boardShareService
            .getStatus(boardId, userId)
            .let(BoardShareStatusResponse::from)
            .let { ApiResponse.success(it) }

    override fun share(
        @AuthenticatedUser userId: UserId,
        @PathVariable boardId: BoardId,
        @Valid @RequestBody request: BoardShareRequest,
    ): ApiResponse<BoardShareResponse> =
        boardShareService
            .share(boardId, userId, request.scope)
            .let(BoardShareResponse::from)
            .let { ApiResponse.success(it) }
}
