package com.github.nexters.ppotto.global.openapi

import com.github.nexters.ppotto.global.error.ErrorResponse
import com.github.nexters.ppotto.global.response.ApiResponse
import java.time.Instant

object ApiExamples {
    val TIMESTAMP: Instant = Instant.parse("2026-07-27T05:02:11Z")

    val UNAUTHORIZED: ApiErrorResponse = error("COMMON-004", "인증이 필요합니다.")

    val EMPTY_SUCCESS: List<ApiExample> = listOf(ApiExample(name = "성공", value = ApiResponse.success()))

    val INVALID_INPUT: ApiExample = errorExample("COMMON-001", "요청 바디 검증 실패", "잘못된 입력입니다.")

    val INVALID_INPUT_WITH_FIELD_ERRORS: ApiExample =
        ApiExample(
            name = "COMMON-001 (필드 오류)",
            summary = "필드별 검증 실패 상세가 함께 내려가는 경우",
            value =
                ApiErrorResponse(
                    success = false,
                    data = null,
                    error =
                        ErrorResponse(
                            code = "COMMON-001",
                            message = "잘못된 입력입니다.",
                            fieldErrors =
                                listOf(
                                    ErrorResponse.FieldErrorDetail(
                                        field = "name",
                                        value = "열자가넘는아주긴보드이름",
                                        reason = "크기가 1에서 10 사이여야 합니다",
                                    ),
                                ),
                            timestamp = TIMESTAMP,
                        ),
                ),
        )

    val INVALID_INPUT_RESPONSE: List<ApiExample> = listOf(INVALID_INPUT, INVALID_INPUT_WITH_FIELD_ERRORS)

    val CONFLICT_RESPONSE: List<ApiExample> =
        listOf(errorExample("COMMON-006", "동시 요청으로 현재 상태와 충돌한 경우", "이미 처리된 요청이거나 충돌이 발생했습니다."))

    fun error(
        code: String,
        message: String,
    ): ApiErrorResponse =
        ApiErrorResponse(
            success = false,
            data = null,
            error = ErrorResponse(code = code, message = message, fieldErrors = emptyList(), timestamp = TIMESTAMP),
        )

    fun errorExample(
        code: String,
        summary: String,
        message: String,
    ): ApiExample = ApiExample(name = code, summary = summary, value = error(code, message))
}
