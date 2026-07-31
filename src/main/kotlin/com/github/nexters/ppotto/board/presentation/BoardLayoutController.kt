package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.application.BoardLayoutService
import com.github.nexters.ppotto.board.presentation.dto.BoardLayoutRequest
import com.github.nexters.ppotto.global.response.ApiResponse
import com.github.nexters.ppotto.global.security.AuthenticatedUser
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class BoardLayoutController(
    private val boardLayoutService: BoardLayoutService,
) : BoardLayoutApi {
    override fun update(
        @AuthenticatedUser userId: UUID,
        @PathVariable boardId: UUID,
        @Valid @RequestBody request: BoardLayoutRequest,
    ): ApiResponse<Unit> =
        boardLayoutService
            .update(boardId, userId, request.toCommand())
            .let { ApiResponse.success() }
}
