package com.github.nexters.ppotto.sticker.presentation

import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.global.openapi.ApiErrorResponse
import com.github.nexters.ppotto.global.web.ApiResponse
import com.github.nexters.ppotto.sticker.presentation.dto.RecapDetailResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

@RequestMapping("/boards/shared/{shareToken}/stickers/{stickerId}", version = "1+")
@Tag(name = "보드 공유", description = "보드 외부 공유 링크 발급과 공유된 보드 조회")
interface SharedBoardRecapApi {
    @GetMapping
    @Operation(
        operationId = "getSharedBoardRecap",
        summary = "공유된 보드의 스티커 리캡 조회",
        description =
            "인증 없이 공유된 보드에 있는 스티커의 리캡을 조회함. 링크의 공유 범위가 BOARD이면 BOARD-011, " +
                "RECAP이면 photos는 항상 빈 배열이고 PHOTOS일 때만 사진을 포함함. 공동 보드의 모든 스티커에 같은 범위를 적용하며 isNew는 항상 false임",
        parameters = [
            Parameter(
                name = "shareToken",
                description = "공유 링크 토큰",
                example = "4f1c2b9e-7a3d-4e8f-9b6a-2c5d8e1f0a7b",
            ),
            Parameter(
                name = "stickerId",
                description = "공유된 보드에 있는 스티커 ID (uuidv7)",
                example = "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
            ),
        ],
    )
    @OpenApiResponse(responseCode = "200", useReturnTypeSchema = true, description = "스티커 리캡 상세")
    @OpenApiResponse(
        responseCode = "404",
        description = "공유된 보드를 찾을 수 없음 (BOARD-010) 또는 공유 범위 밖이거나 이 보드의 스티커가 아님 (BOARD-011)",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))],
    )
    fun get(
        shareToken: String,
        stickerId: StickerId,
    ): ApiResponse<RecapDetailResponse>
}
