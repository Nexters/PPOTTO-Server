package com.github.nexters.ppotto.auth.presentation.dto

import com.github.nexters.ppotto.auth.domain.TokenPair
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "재발급된 서비스 토큰")
data class TokenPairResponse(
    @field:Schema(
        description = "JWT. Authorization Bearer 헤더에 넣음",
        example = "eyJhbGciOiJIUzI1NiJ9.sample-access-token.sample-signature",
    )
    val accessToken: String,

    @field:Schema(
        description = "서버가 생성한 랜덤 값 (JWT 아님). Keychain 등 보안 저장소에 보관",
        example = "sample-refresh-token-01983f2a4d5e7f6a",
    )
    val refreshToken: String,

    @field:Schema(description = "accessToken 만료까지 남은 초", example = "3600")
    val accessTokenExpiresIn: Long,
) {
    companion object {
        fun from(tokenPair: TokenPair): TokenPairResponse =
            TokenPairResponse(
                accessToken = tokenPair.accessToken,
                refreshToken = tokenPair.refreshToken,
                accessTokenExpiresIn = tokenPair.accessTokenExpiresIn,
            )
    }
}
