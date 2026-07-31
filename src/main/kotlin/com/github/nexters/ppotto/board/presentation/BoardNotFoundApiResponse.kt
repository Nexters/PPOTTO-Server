package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.global.openapi.ApiErrorResponse
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponse(
    responseCode = "404",
    description = "보드를 찾을 수 없음 (BOARD-002)",
    content = [
        Content(
            mediaType = "application/json",
            schema = Schema(implementation = ApiErrorResponse::class),
        ),
    ],
)
annotation class BoardNotFoundApiResponse
