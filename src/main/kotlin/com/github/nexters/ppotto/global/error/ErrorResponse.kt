package com.github.nexters.ppotto.global.error

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.validation.BindingResult
import java.time.Instant

@Schema(description = "실패 응답 상세")
data class ErrorResponse(
    @field:Schema(description = "클라이언트 분기 기준이 되는 에러 코드", example = "COMMON-001")
    val code: String,
    @field:Schema(description = "사용자 또는 개발자 확인용 메시지", example = "잘못된 입력입니다.")
    val message: String,
    @field:Schema(description = "요청 바디 검증 실패 시 필드별 오류. 그 외 빈 배열")
    val fieldErrors: List<FieldErrorDetail>,
    @field:Schema(description = "에러 발생 시각. UTC ISO-8601", example = "2026-07-27T05:02:11Z")
    val timestamp: Instant,
) {
    @Schema(description = "요청 바디 필드 검증 실패 상세")
    data class FieldErrorDetail(
        @field:Schema(description = "검증에 실패한 필드", example = "name")
        val field: String,
        @field:Schema(description = "요청에 담겨 온 값", example = "열자가넘는아주긴보드이름")
        val value: String?,
        @field:Schema(description = "실패 사유", example = "크기가 1에서 10 사이여야 합니다")
        val reason: String?,
    )

    companion object {
        fun of(
            errorCode: ErrorCode,
            message: String = errorCode.message,
        ): ErrorResponse = ErrorResponse(errorCode.code, message, emptyList(), Instant.now())

        fun of(
            errorCode: ErrorCode,
            bindingResult: BindingResult,
        ): ErrorResponse =
            ErrorResponse(
                errorCode.code,
                errorCode.message,
                bindingResult.fieldErrors.map {
                    FieldErrorDetail(it.field, it.rejectedValue?.toString(), it.defaultMessage)
                },
                Instant.now(),
            )
    }
}
