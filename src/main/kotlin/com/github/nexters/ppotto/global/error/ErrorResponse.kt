package com.github.nexters.ppotto.global.error

import org.springframework.validation.BindingResult
import java.time.Instant

data class ErrorResponse(
    val code: String,
    val message: String,
    val fieldErrors: List<FieldErrorDetail>,
    val timestamp: Instant,
) {
    data class FieldErrorDetail(
        val field: String,
        val value: String?,
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
