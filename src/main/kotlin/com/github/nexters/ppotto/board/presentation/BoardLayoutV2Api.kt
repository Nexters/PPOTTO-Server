package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.presentation.dto.BoardLayoutV2Request
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.openapi.ApiErrorResponse
import com.github.nexters.ppotto.global.openapi.EmptySuccessApiResponse
import com.github.nexters.ppotto.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestMapping
import io.swagger.v3.oas.annotations.parameters.RequestBody as OpenApiRequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

@RequestMapping("/boards", version = "2+")
@Tag(name = "보드", description = "보드 조회와 관리")
interface BoardLayoutV2Api {
    @PatchMapping("/{boardId}/layout")
    @Operation(
        operationId = "updateV2",
        summary = "보드 편집 결과 저장 (v2)",
        description =
            "스티커 배치와 선·텍스트의 생성·삭제를 하나의 트랜잭션으로 반영함. 편집 모드에서 바뀐 필드만 보냄. " +
                "created는 id 기준 upsert라 같은 id를 다시 보내면 수정이고, 삭제한 id를 다시 보내면 되살아남",
        parameters = [
            Parameter(
                name = "boardId",
                description = "편집한 보드 ID (uuidv7)",
                example = "01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b",
            ),
        ],
        requestBody =
            OpenApiRequestBody(
                required = true,
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = BoardLayoutV2Request::class),
                    ),
                ],
            ),
    )
    @EmptySuccessApiResponse
    @OpenApiResponse(
        responseCode = "400",
        description = "요청 값이 올바르지 않음 (COMMON-001, BOARD-001)",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ApiErrorResponse::class),
            ),
        ],
    )
    @BoardNotFoundApiResponse
    fun update(
        userId: UserId,
        boardId: BoardId,
        request: BoardLayoutV2Request,
    ): ApiResponse<Unit>
}
