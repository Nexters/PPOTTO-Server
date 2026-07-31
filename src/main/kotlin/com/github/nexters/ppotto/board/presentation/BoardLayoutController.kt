package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.application.BoardLayoutService
import com.github.nexters.ppotto.board.presentation.dto.BoardLayoutRequest
import com.github.nexters.ppotto.global.openapi.InvalidInputApiResponse
import com.github.nexters.ppotto.global.openapi.NotFoundApiResponse
import com.github.nexters.ppotto.global.response.ApiResponse
import com.github.nexters.ppotto.global.security.AuthenticatedUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/boards", version = "1")
@Tag(name = "보드", description = "보드 조회와 관리")
class BoardLayoutController(
    private val boardLayoutService: BoardLayoutService,
) {
    @PatchMapping("/{boardId}/layout")
    @Operation(
        summary = "보드 편집 결과 저장",
        description = "스티커 배치와 그림 생성·삭제를 하나의 트랜잭션으로 반영함",
    )
    @InvalidInputApiResponse
    @NotFoundApiResponse
    fun update(
        @AuthenticatedUser userId: UUID,
        @PathVariable boardId: UUID,
        @Valid @RequestBody request: BoardLayoutRequest,
    ): ApiResponse<Unit> {
        boardLayoutService.update(boardId, userId, request.toCommand())
        return ApiResponse.success()
    }
}
