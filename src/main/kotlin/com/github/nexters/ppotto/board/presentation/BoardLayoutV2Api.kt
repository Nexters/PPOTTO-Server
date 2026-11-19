package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.presentation.dto.BoardLayoutV2Request
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
            "스티커 배치와 선·텍스트의 생성·수정·삭제를 하나의 트랜잭션으로 반영함. " +
                "변경된 항목만 보내며, 기존 항목의 수정·삭제는 조회 당시 배치 버전이 일치할 때만 반영함. " +
                "충돌하면 변경사항을 적용하지 않고 충돌한 ID를 반환함",
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
        description = "요청 값이 올바르지 않음 (COMMON-001, BOARD-001, STICKER-008)",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ApiErrorResponse::class),
            ),
        ],
    )
    @BoardNotFoundApiResponse
    @OpenApiResponse(
        responseCode = "409",
        description = "조회한 뒤 스티커 또는 선·텍스트 배치가 변경되거나 삭제됨 (BOARD-013). error.details에 충돌한 ID 목록 포함",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))],
    )
    fun update(
        userId: UserId,
        boardId: BoardId,
        request: BoardLayoutV2Request,
    ): ApiResponse<Unit>
}
