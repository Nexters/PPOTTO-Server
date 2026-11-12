package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

@RequestMapping("/boards/{boardId}/events", version = "1+")
@Tag(name = "보드", description = "보드 조회와 관리")
interface BoardEventApi {
    @GetMapping(produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @Operation(
        operationId = "subscribeBoardEvents",
        summary = "공동 보드 변경 이벤트 구독",
        description = "접근 가능한 보드의 레이아웃 확정 이벤트를 SSE로 구독함",
        parameters = [
            Parameter(
                name = "boardId",
                description = "구독할 보드 ID (uuidv7)",
                example = "01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b",
            ),
        ],
    )
    @OpenApiResponse(
        responseCode = "200",
        description = "보드 변경 이벤트 스트림",
        content = [
            Content(
                mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                schema = Schema(type = "string"),
            ),
        ],
    )
    @BoardNotFoundApiResponse
    fun subscribe(
        userId: UserId,
        boardId: BoardId,
    ): SseEmitter
}
