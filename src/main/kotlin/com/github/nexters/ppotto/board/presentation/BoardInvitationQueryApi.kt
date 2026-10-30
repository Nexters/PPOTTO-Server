package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.presentation.dto.BoardInvitationDetailResponse
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.openapi.ApiErrorResponse
import com.github.nexters.ppotto.global.web.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

@RequestMapping("/board-invitations/{code}", version = "1+")
@Tag(name = "보드 초대", description = "보드 초대 조회와 관리")
interface BoardInvitationQueryApi {
    @GetMapping
    @Operation(
        operationId = "getBoardInvitation",
        summary = "보드 초대 상세 조회",
        description = "인증 없이 조회할 수 있으며 로그인한 사용자는 참여 상태와 참여자 목록도 함께 확인함",
        parameters = [
            Parameter(name = "code", description = "6자리 초대 코드", example = "K7P2QX"),
        ],
    )
    @OpenApiResponse(responseCode = "200", useReturnTypeSchema = true, description = "유효한 초대 상세")
    @OpenApiResponse(
        responseCode = "404",
        description = "초대를 찾을 수 없음 (BOARD-006)",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))],
    )
    fun get(
        userId: UserId?,
        code: String,
    ): ApiResponse<BoardInvitationDetailResponse>
}
