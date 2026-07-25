package com.github.nexters.ppotto.global.error

import java.time.Instant

data class ErrorResponse(
    val code: String,
    val message: String,
    val timestamp: Instant = Instant.now(),
) {
    companion object {
        fun of(errorCode: ErrorCode, message: String = errorCode.message): ErrorResponse =
            ErrorResponse(errorCode.code, message)
    }
}
