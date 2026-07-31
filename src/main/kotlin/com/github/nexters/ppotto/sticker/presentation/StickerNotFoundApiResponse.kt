package com.github.nexters.ppotto.sticker.presentation

import com.github.nexters.ppotto.global.openapi.ApiErrorResponse
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponse(
    responseCode = "404",
    description = "스티커를 찾을 수 없음 (STICKER-001)",
    content = [
        Content(
            mediaType = "application/json",
            schema = Schema(implementation = ApiErrorResponse::class),
        ),
    ],
)
annotation class StickerNotFoundApiResponse
