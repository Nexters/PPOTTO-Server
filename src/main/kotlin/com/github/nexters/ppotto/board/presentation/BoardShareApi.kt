package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.presentation.dto.BoardShareRequest
import com.github.nexters.ppotto.board.presentation.dto.BoardShareResponse
import com.github.nexters.ppotto.board.presentation.dto.BoardShareStatusResponse
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.openapi.InvalidInputApiResponse
import com.github.nexters.ppotto.global.web.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import io.swagger.v3.oas.annotations.parameters.RequestBody as OpenApiRequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

@RequestMapping("/boards/{boardId}/share", version = "1+")
@Tag(name = "보드 공유", description = "보드 외부 공유 링크 발급과 공유된 보드 조회")
interface BoardShareApi {
    @GetMapping
    @Operation(
        operationId = "getMyBoardShare",
        summary = "내 보드 공유 상태 조회",
        description = "보드의 방장 또는 참여자가 자기 공유 링크 토큰과 현재 범위를 조회함. 공유한 적이 없으면 shareToken은 null이고 scope는 기본값 RECAP임",
        parameters = [
            Parameter(
                name = "boardId",
                description = "공유 상태를 확인할 보드 ID (uuidv7)",
                example = "01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b",
            ),
        ],
    )
    @OpenApiResponse(
        responseCode = "200",
        useReturnTypeSchema = true,
        description = "내 공유 링크 상태",
    )
    @BoardNotFoundApiResponse
    fun getStatus(
        userId: UserId,
        boardId: BoardId,
    ): ApiResponse<BoardShareStatusResponse>

    @PostMapping
    @Operation(
        operationId = "shareBoard",
        summary = "내 보드 공유 링크 발급·범위 변경",
        description = "보드의 방장 또는 참여자에게 자기 이름의 공유 링크 토큰을 발급함. 이미 공유했다면 같은 토큰을 유지하고 범위만 바꿈",
        parameters = [
            Parameter(
                name = "boardId",
                description = "공유할 보드 ID (uuidv7)",
                example = "01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b",
            ),
        ],
        requestBody =
            OpenApiRequestBody(
                required = true,
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = BoardShareRequest::class),
                    ),
                ],
            ),
    )
    @OpenApiResponse(
        responseCode = "200",
        useReturnTypeSchema = true,
        description = "내 공유 링크 정보",
    )
    @InvalidInputApiResponse
    @BoardNotFoundApiResponse
    fun share(
        userId: UserId,
        boardId: BoardId,
        request: BoardShareRequest,
    ): ApiResponse<BoardShareResponse>
}
