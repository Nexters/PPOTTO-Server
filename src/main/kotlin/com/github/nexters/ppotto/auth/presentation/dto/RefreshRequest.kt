package com.github.nexters.ppotto.auth.presentation.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "토큰 재발급 요청")
data class RefreshRequest(
    @field:NotBlank
    @field:Schema(
        description = "로그인 또는 이전 재발급에서 받은 refresh token",
        example = "sample-refresh-token-01983f2a7c317b02",
    )
    val refreshToken: String,
)
