package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.presentation.dto.BoardApiExamples
import com.github.nexters.ppotto.board.presentation.dto.BoardLayoutRequest
import com.github.nexters.ppotto.global.openapi.ApiErrorResponse
import com.github.nexters.ppotto.global.openapi.ApiExamples
import com.github.nexters.ppotto.global.openapi.EmptySuccessApiResponse
import com.github.nexters.ppotto.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestMapping
import java.util.UUID
import io.swagger.v3.oas.annotations.parameters.RequestBody as OpenApiRequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

@RequestMapping("/boards", version = "1")
@Tag(name = "보드", description = "보드 조회와 관리")
interface BoardLayoutApi {
    @PatchMapping("/{boardId}/layout")
    @Operation(
        summary = "보드 편집 결과 저장",
        description = "스티커 배치와 그림 생성·삭제를 하나의 트랜잭션으로 반영함. 편집 모드에서 바뀐 필드만 보냄",
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
                        schema = Schema(implementation = BoardLayoutRequest::class),
                        examples = [
                            ExampleObject(
                                name = "스티커 이동 모드 종료",
                                value = BoardApiExamples.STICKER_MOVE_LAYOUT_REQUEST,
                            ),
                            ExampleObject(
                                name = "텍스트 모드 종료 (제목 + 뱃지 배치)",
                                value = BoardApiExamples.TEXT_MODE_LAYOUT_REQUEST,
                            ),
                            ExampleObject(
                                name = "드로잉 모드 종료 (생성 2건, 삭제 1건)",
                                value = BoardApiExamples.DRAWING_MODE_LAYOUT_REQUEST,
                            ),
                        ],
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
                examples = [
                    ExampleObject(
                        name = "COMMON-001",
                        summary = "필드 형식 오류 (제목 15자 초과 등)",
                        value = ApiExamples.INVALID_INPUT,
                    ),
                    ExampleObject(
                        name = "BOARD-001",
                        summary = "소유하지 않은 항목 포함. 부분 저장 없이 전체 거부",
                        value = BoardApiExamples.INVALID_LAYOUT,
                    ),
                ],
            ),
        ],
    )
    @BoardNotFoundApiResponse
    fun update(
        userId: UUID,
        boardId: UUID,
        request: BoardLayoutRequest,
    ): ApiResponse<Unit>
}
