package com.github.nexters.ppotto.global.openapi

import com.github.nexters.ppotto.global.error.ErrorResponse
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "공통 실패 응답 봉투")
data class ApiErrorResponse(
    @field:Schema(description = "요청 성공 여부", example = "false")
    val success: Boolean,
    @field:Schema(description = "실패 시 항상 null")
    val data: Any?,
    @field:Schema(description = "에러 코드와 메시지")
    val error: ErrorResponse,
)
