package com.github.nexters.ppotto.global.openapi

import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponse(
    responseCode = "400",
    description = "요청 값이 올바르지 않음 (COMMON-001)",
    content = [
        Content(
            mediaType = "application/json",
            schema = Schema(implementation = ApiErrorResponse::class),
        ),
    ],
)
annotation class InvalidInputApiResponse

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponse(
    responseCode = "409",
    description = "현재 상태와 요청이 충돌함 (COMMON-006)",
    content = [
        Content(
            mediaType = "application/json",
            schema = Schema(implementation = ApiErrorResponse::class),
        ),
    ],
)
annotation class ConflictApiResponse

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponse(
    responseCode = "200",
    useReturnTypeSchema = true,
    description = "처리 완료. data는 항상 null",
)
annotation class EmptySuccessApiResponse
