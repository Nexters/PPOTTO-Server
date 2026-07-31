package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.application.BoardLayoutService
import com.github.nexters.ppotto.board.presentation.dto.BoardLayoutRequest
import com.github.nexters.ppotto.global.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/boards", version = "1")
class BoardLayoutController(
    private val boardLayoutService: BoardLayoutService,
) {
    @PatchMapping("/{boardId}/layout")
    fun update(
        @AuthenticationPrincipal userId: UUID?,
        @PathVariable boardId: UUID,
        @Valid @RequestBody request: BoardLayoutRequest,
    ): ApiResponse<Unit> {
        boardLayoutService.update(boardId, userId.requireAuthenticatedUserId(), request.toCommand())
        return ApiResponse.success()
    }
}
