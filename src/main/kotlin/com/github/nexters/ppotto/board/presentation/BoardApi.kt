package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.presentation.dto.BoardResponse
import com.github.nexters.ppotto.board.presentation.dto.CreateBoardRequest
import com.github.nexters.ppotto.board.presentation.dto.RenameBoardRequest
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.openapi.ApiErrorResponse
import com.github.nexters.ppotto.global.openapi.ConflictApiResponse
import com.github.nexters.ppotto.global.openapi.EmptySuccessApiResponse
import com.github.nexters.ppotto.global.openapi.InvalidInputApiResponse
import com.github.nexters.ppotto.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import io.swagger.v3.oas.annotations.parameters.RequestBody as OpenApiRequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

@RequestMapping("/boards", version = "1+")
@Tag(name = "보드", description = "보드 조회와 관리")
interface BoardApi {
    @GetMapping
    @Operation(
        operationId = "list",
        summary = "내 보드 목록 조회",
        description = "보드 선택 드롭다운에 사용할 활성 보드 목록을 반환함. id(uuidv7) 오름차순이 생성순",
    )
    @OpenApiResponse(
        responseCode = "200",
        useReturnTypeSchema = true,
        description = "보드 목록",
    )
    fun list(userId: UserId): ApiResponse<List<BoardResponse>>

    @PostMapping
    @Operation(
        operationId = "create",
        summary = "보드 생성",
        description = "새 보드를 만들며 이름을 생략하면 순서에 맞는 기본 이름을 사용함. 사용자당 최대 100개",
        requestBody =
            OpenApiRequestBody(
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = CreateBoardRequest::class),
                    ),
                ],
            ),
    )
    @OpenApiResponse(
        responseCode = "200",
        useReturnTypeSchema = true,
        description = "생성된 보드",
    )
    @OpenApiResponse(
        responseCode = "400",
        description = "요청 값이 올바르지 않음 (COMMON-001, BOARD-003)",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ApiErrorResponse::class),
            ),
        ],
    )
    @ConflictApiResponse
    fun create(
        userId: UserId,
        request: CreateBoardRequest,
    ): ApiResponse<BoardResponse>

    @PatchMapping("/{boardId}")
    @Operation(
        operationId = "rename",
        summary = "보드 이름 변경",
        description = "내 보드의 이름을 변경함. 최대 10자",
        parameters = [
            Parameter(
                name = "boardId",
                description = "이름을 바꿀 보드 ID (uuidv7)",
                example = "01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b",
            ),
        ],
        requestBody =
            OpenApiRequestBody(
                required = true,
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = RenameBoardRequest::class),
                    ),
                ],
            ),
    )
    @OpenApiResponse(
        responseCode = "200",
        useReturnTypeSchema = true,
        description = "변경된 보드",
    )
    @InvalidInputApiResponse
    @BoardNotFoundApiResponse
    fun rename(
        userId: UserId,
        boardId: BoardId,
        request: RenameBoardRequest,
    ): ApiResponse<BoardResponse>

    @DeleteMapping("/{boardId}")
    @Operation(
        operationId = "delete_1",
        summary = "보드 삭제",
        description = "보드와 그 위의 스티커, 리캡, 그림을 함께 삭제함. 마지막 보드나 분석 중인 보드는 삭제할 수 없음",
        parameters = [
            Parameter(
                name = "boardId",
                description = "삭제할 보드 ID (uuidv7)",
                example = "01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b",
            ),
        ],
    )
    @EmptySuccessApiResponse
    @BoardNotFoundApiResponse
    @OpenApiResponse(
        responseCode = "409",
        description = "현재 상태와 요청이 충돌함 (BOARD-004, BOARD-005)",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ApiErrorResponse::class),
            ),
        ],
    )
    fun delete(
        userId: UserId,
        boardId: BoardId,
    ): ApiResponse<Unit>
}
