package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.application.BoardLayoutService
import com.github.nexters.ppotto.board.presentation.dto.BoardLayoutV2Request
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.response.ApiResponse
import com.github.nexters.ppotto.global.security.AuthenticatedUser
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class BoardLayoutV2Controller(
    private val boardLayoutService: BoardLayoutService,
) : BoardLayoutV2Api {
    override fun update(
        @AuthenticatedUser userId: UserId,
        @PathVariable boardId: BoardId,
        @Valid @RequestBody request: BoardLayoutV2Request,
    ): ApiResponse<Unit> =
        boardLayoutService
            .update(boardId, userId, request.toCommand())
            .let { ApiResponse.success() }
}
