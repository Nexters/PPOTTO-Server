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

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponse(
    responseCode = "400",
    description = "요청 값이 올바르지 않음 (COMMON-001, STICKER-004)",
    content = [
        Content(
            mediaType = "application/json",
            schema = Schema(implementation = ApiErrorResponse::class),
        ),
    ],
)
annotation class UneditableRecapCommentApiResponse

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponse(
    responseCode = "400",
    description = "재생성할 수 없는 스티커임 (STICKER-005, STICKER-006)",
    content = [
        Content(
            mediaType = "application/json",
            schema = Schema(implementation = ApiErrorResponse::class),
        ),
    ],
)
annotation class NotRegeneratableStickerApiResponse

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponse(
    responseCode = "409",
    description = "같은 스티커에 대한 재생성이 이미 진행 중임 (STICKER-002)",
    content = [
        Content(
            mediaType = "application/json",
            schema = Schema(implementation = ApiErrorResponse::class),
        ),
    ],
)
annotation class StickerRegenerationInProgressApiResponse
