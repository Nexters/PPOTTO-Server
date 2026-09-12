package com.github.nexters.ppotto.global.openapi

import com.github.nexters.ppotto.global.error.CommonErrorCode
import com.github.nexters.ppotto.global.error.ErrorCode
import com.github.nexters.ppotto.global.error.ErrorResponse
import com.github.nexters.ppotto.global.web.ApiResponse
import java.time.Instant

object ApiExamples {
    private val TIMESTAMP: Instant = Instant.parse("2026-07-27T05:02:11Z")

    val UNAUTHORIZED: ApiErrorResponse = error(CommonErrorCode.UNAUTHORIZED)

    val EMPTY_SUCCESS: List<ApiExample> = listOf(ApiExample(name = "성공", value = ApiResponse.success()))

    val INVALID_INPUT: ApiExample = errorExample(CommonErrorCode.INVALID_INPUT, "요청 바디 검증 실패")

    val INVALID_INPUT_WITH_FIELD_ERRORS: ApiExample =
        ApiExample(
            name = "${CommonErrorCode.INVALID_INPUT.code} (필드 오류)",
            summary = "필드별 검증 실패 상세가 함께 내려가는 경우",
            value =
                ApiErrorResponse(
                    success = false,
                    data = null,
                    error =
                        ErrorResponse(
                            code = CommonErrorCode.INVALID_INPUT.code,
                            message = CommonErrorCode.INVALID_INPUT.message,
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
        listOf(errorExample(CommonErrorCode.CONFLICT, "동시 요청으로 현재 상태와 충돌한 경우"))

    fun crossDomainErrorExample(
        code: String,
        summary: String,
        message: String,
    ): ApiExample = ApiExample(name = code, summary = summary, value = error(code, message))

    private fun error(errorCode: ErrorCode): ApiErrorResponse = error(errorCode.code, errorCode.message)

    private fun error(
        code: String,
        message: String,
    ): ApiErrorResponse =
        ApiErrorResponse(
            success = false,
            data = null,
            error = ErrorResponse(code = code, message = message, fieldErrors = emptyList(), timestamp = TIMESTAMP),
        )

    fun errorExample(
        errorCode: ErrorCode,
        summary: String,
    ): ApiExample = ApiExample(name = errorCode.code, summary = summary, value = error(errorCode))
}
