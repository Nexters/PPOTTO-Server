package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.application.BoardCommandService
import com.github.nexters.ppotto.board.application.BoardQueryService
import com.github.nexters.ppotto.board.presentation.dto.BoardDetailResponse
import com.github.nexters.ppotto.board.presentation.dto.BoardResponse
import com.github.nexters.ppotto.board.presentation.dto.CreateBoardRequest
import com.github.nexters.ppotto.board.presentation.dto.RenameBoardRequest
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.response.ApiResponse
import com.github.nexters.ppotto.global.security.AuthenticatedUser
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class BoardController(
    private val boardCommandService: BoardCommandService,
    private val boardQueryService: BoardQueryService,
) : BoardApi {
    override fun list(
        @AuthenticatedUser userId: UUID,
    ): ApiResponse<List<BoardResponse>> =
        boardQueryService
            .list(UserId(userId))
            .map(BoardResponse::from)
            .let { ApiResponse.success(it) }

    override fun create(
        @AuthenticatedUser userId: UUID,
        @Valid @RequestBody request: CreateBoardRequest,
    ): ApiResponse<BoardResponse> =
        boardCommandService
            .create(UserId(userId), request.name)
            .let(BoardResponse::from)
            .let { ApiResponse.success(it) }

    override fun get(
        @AuthenticatedUser userId: UUID,
        @PathVariable boardId: UUID,
    ): ApiResponse<BoardDetailResponse> =
        boardQueryService
            .getDetail(BoardId(boardId), UserId(userId))
            .let(BoardDetailResponse::from)
            .let { ApiResponse.success(it) }

    override fun rename(
        @AuthenticatedUser userId: UUID,
        @PathVariable boardId: UUID,
        @Valid @RequestBody request: RenameBoardRequest,
    ): ApiResponse<BoardResponse> =
        boardCommandService
            .rename(BoardId(boardId), UserId(userId), request.name)
            .let(BoardResponse::from)
            .let { ApiResponse.success(it) }

    override fun delete(
        @AuthenticatedUser userId: UUID,
        @PathVariable boardId: UUID,
    ): ApiResponse<Unit> =
        boardCommandService
            .delete(BoardId(boardId), UserId(userId))
            .let { ApiResponse.success() }
}
