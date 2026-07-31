package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.application.BoardCommandService
import com.github.nexters.ppotto.board.application.BoardQueryService
import com.github.nexters.ppotto.board.presentation.dto.BoardDetailResponse
import com.github.nexters.ppotto.board.presentation.dto.BoardResponse
import com.github.nexters.ppotto.board.presentation.dto.CreateBoardRequest
import com.github.nexters.ppotto.board.presentation.dto.RenameBoardRequest
import com.github.nexters.ppotto.global.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/boards", version = "1")
class BoardController(
    private val boardCommandService: BoardCommandService,
    private val boardQueryService: BoardQueryService,
) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal userId: UUID?,
    ): ApiResponse<List<BoardResponse>> =
        ApiResponse.success(
            boardQueryService.list(userId.requireAuthenticatedUserId()).map(BoardResponse::from),
        )

    @PostMapping
    fun create(
        @AuthenticationPrincipal userId: UUID?,
        @Valid @RequestBody request: CreateBoardRequest,
    ): ApiResponse<BoardResponse> =
        ApiResponse.success(
            BoardResponse.from(boardCommandService.create(userId.requireAuthenticatedUserId(), request.name)),
        )

    @GetMapping("/{boardId}")
    fun get(
        @AuthenticationPrincipal userId: UUID?,
        @PathVariable boardId: UUID,
    ): ApiResponse<BoardDetailResponse> =
        ApiResponse.success(
            BoardDetailResponse.from(boardQueryService.getDetail(boardId, userId.requireAuthenticatedUserId())),
        )

    @PatchMapping("/{boardId}")
    fun rename(
        @AuthenticationPrincipal userId: UUID?,
        @PathVariable boardId: UUID,
        @Valid @RequestBody request: RenameBoardRequest,
    ): ApiResponse<BoardResponse> =
        ApiResponse.success(
            BoardResponse.from(boardCommandService.rename(boardId, userId.requireAuthenticatedUserId(), request.name)),
        )

    @DeleteMapping("/{boardId}")
    fun delete(
        @AuthenticationPrincipal userId: UUID?,
        @PathVariable boardId: UUID,
    ): ApiResponse<Unit> {
        boardCommandService.delete(boardId, userId.requireAuthenticatedUserId())
        return ApiResponse.success()
    }
}
