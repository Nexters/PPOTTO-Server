package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.presentation.dto.BoardJoinResponse
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.openapi.ApiErrorResponse
import com.github.nexters.ppotto.global.web.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

@RequestMapping("/board-invitations/{code}/join", version = "1+")
@Tag(name = "보드 초대", description = "보드 초대 조회와 관리")
interface BoardParticipationApi {
    @PostMapping
    @Operation(
        operationId = "joinBoardByInvitation",
        summary = "초대를 통한 보드 참여",
        description = "초대 링크 또는 6자리 초대 코드로 전달된 초대를 사용해 보드에 참여함",
        parameters = [
            Parameter(name = "code", description = "6자리 초대 코드", example = "K7P2QX"),
        ],
    )
    @OpenApiResponse(responseCode = "200", useReturnTypeSchema = true, description = "보드 참여 또는 기존 참여 상태")
    @OpenApiResponse(
        responseCode = "404",
        description = "초대를 찾을 수 없음 (BOARD-006)",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))],
    )
    @OpenApiResponse(
        responseCode = "409",
        description = "보드 참여 인원이 가득 참 (BOARD-009)",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))],
    )
    fun join(
        userId: UserId,
        code: String,
    ): ApiResponse<BoardJoinResponse>
}
