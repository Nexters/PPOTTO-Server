package com.github.nexters.ppotto.global.response

import com.github.nexters.ppotto.global.error.ErrorResponse
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "공통 응답 봉투")
data class ApiResponse<T>(
    @field:Schema(description = "요청 성공 여부", example = "true")
    val success: Boolean,
    @field:Schema(description = "성공 시 응답 데이터. 실패 시 null")
    val data: T?,
    @field:Schema(description = "실패 시 에러 상세. 성공 시 null")
    val error: ErrorResponse?,
) {
    companion object {
        fun <T> success(data: T): ApiResponse<T> = ApiResponse(true, data, null)

        fun success(): ApiResponse<Unit> = ApiResponse(true, null, null)

        fun error(error: ErrorResponse): ApiResponse<Unit> = ApiResponse(false, null, error)
    }
}
