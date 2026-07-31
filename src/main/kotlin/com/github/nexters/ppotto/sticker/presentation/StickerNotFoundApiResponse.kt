package com.github.nexters.ppotto.sticker.presentation

import com.github.nexters.ppotto.global.openapi.ApiErrorResponse
import com.github.nexters.ppotto.sticker.presentation.dto.StickerApiExamples
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
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
            examples = [
                ExampleObject(
                    name = "STICKER-001",
                    summary = "스티커 없음 또는 소유자 불일치",
                    value = StickerApiExamples.STICKER_NOT_FOUND,
                ),
            ],
        ),
    ],
)
annotation class StickerNotFoundApiResponse
