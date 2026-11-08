package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.presentation.dto.SharedBoardResponse
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

@RequestMapping("/boards/shared/{shareToken}", version = "1+")
@Tag(name = "보드 공유", description = "보드 외부 공유 링크 발급과 공유된 보드 조회")
interface BoardShareQueryApi {
    @GetMapping
    @Operation(
        operationId = "getSharedBoard",
        summary = "공유된 보드 조회",
        description =
            "인증 없이 공유 토큰으로 보드를 조회함. 공동 보드는 모든 참여자의 스티커를 포함하고 isNew는 항상 false임. " +
                "없는 토큰, 삭제된 보드, 탈퇴했거나 보드를 나간 공유자는 모두 BOARD-010으로 응답함",
        parameters = [
            Parameter(
                name = "shareToken",
                description = "공유 링크 토큰",
                example = "4f1c2b9e-7a3d-4e8f-9b6a-2c5d8e1f0a7b",
            ),
        ],
    )
    @OpenApiResponse(responseCode = "200", useReturnTypeSchema = true, description = "공유된 보드")
    @OpenApiResponse(
        responseCode = "404",
        description = "공유된 보드를 찾을 수 없음 (BOARD-010)",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))],
    )
    fun get(shareToken: String): ApiResponse<SharedBoardResponse>
}
