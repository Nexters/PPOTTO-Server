package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.presentation.dto.BoardParticipantsResponse
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.web.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

@RequestMapping("/boards/{boardId}/participants", version = "1+")
@Tag(name = "보드", description = "보드 관리")
interface BoardParticipantApi {
    @GetMapping
    @Operation(
        operationId = "getBoardParticipants",
        summary = "보드 참여자 목록 조회",
        description = "접근 가능한 보드의 현재 참여자 목록을 방장 우선, 이후 참여 순서로 조회함",
        parameters = [
            Parameter(
                name = "boardId",
                description = "조회할 보드 ID (uuidv7)",
                example = "01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b",
            ),
        ],
    )
    @OpenApiResponse(
        responseCode = "200",
        useReturnTypeSchema = true,
        description = "현재 참여 인원과 참여자 목록",
    )
    @BoardNotFoundApiResponse
    fun get(
        userId: UserId,
        boardId: BoardId,
    ): ApiResponse<BoardParticipantsResponse>
}
