package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.openapi.ApiErrorResponse
import com.github.nexters.ppotto.global.openapi.EmptySuccessApiResponse
import com.github.nexters.ppotto.global.web.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.RequestMapping
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

@RequestMapping("/boards/{boardId}/members/me", version = "1+")
@Tag(name = "보드 초대", description = "보드 초대 조회와 관리")
interface BoardLeaveApi {
    @DeleteMapping
    @Operation(
        operationId = "leaveBoard",
        summary = "보드 나가기",
        description = "참여 중인 공동 보드에서 나감. 방장은 나갈 수 없음",
        parameters = [
            Parameter(
                name = "boardId",
                description = "나갈 보드 ID (uuidv7)",
                example = "01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b",
            ),
        ],
    )
    @EmptySuccessApiResponse
    @BoardNotFoundApiResponse
    @OpenApiResponse(
        responseCode = "409",
        description = "방장은 나갈 수 없음 (BOARD-012)",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))],
    )
    fun leave(
        userId: UserId,
        boardId: BoardId,
    ): ApiResponse<Unit>
}
