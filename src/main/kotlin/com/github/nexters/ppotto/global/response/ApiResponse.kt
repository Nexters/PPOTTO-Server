package com.github.nexters.ppotto.global.response

import com.github.nexters.ppotto.global.error.ErrorResponse

data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val error: ErrorResponse?,
) {
    companion object {
        fun <T> success(data: T): ApiResponse<T> = ApiResponse(true, data, null)

        fun success(): ApiResponse<Unit> = ApiResponse(true, null, null)

        fun error(error: ErrorResponse): ApiResponse<Unit> = ApiResponse(false, null, error)
    }
}
