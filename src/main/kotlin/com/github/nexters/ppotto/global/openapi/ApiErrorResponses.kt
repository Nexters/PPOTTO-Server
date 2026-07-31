package com.github.nexters.ppotto.global.openapi

import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
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
            examples = [
                ExampleObject(
                    name = "COMMON-001",
                    summary = "요청 바디 검증 실패",
                    value = ApiExamples.INVALID_INPUT,
                ),
                ExampleObject(
                    name = "COMMON-001 (필드 오류)",
                    summary = "필드별 검증 실패 상세가 함께 내려가는 경우",
                    value = ApiExamples.INVALID_INPUT_WITH_FIELD_ERRORS,
                ),
            ],
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
            examples = [
                ExampleObject(
                    name = "COMMON-006",
                    summary = "동시 요청으로 현재 상태와 충돌한 경우",
                    value = ApiExamples.CONFLICT,
                ),
            ],
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
    content = [
        Content(
            examples = [
                ExampleObject(
                    name = "성공",
                    value = ApiExamples.SUCCESS_EMPTY,
                ),
            ],
        ),
    ],
)
annotation class EmptySuccessApiResponse
