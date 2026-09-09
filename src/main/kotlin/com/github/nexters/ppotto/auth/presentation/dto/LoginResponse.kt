package com.github.nexters.ppotto.auth.presentation.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.nexters.ppotto.auth.domain.LoginResult
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "로그인 결과와 미동의 약관")
data class LoginResponse(
    @field:Schema(
        description = "JWT. Authorization Bearer 헤더에 넣음",
        example = "eyJhbGciOiJIUzI1NiJ9.sample-access-token.sample-signature",
    )
    val accessToken: String,

    @field:Schema(
        description = "서버가 생성한 랜덤 값 (JWT 아님). Keychain 등 보안 저장소에 보관",
        example = "sample-refresh-token-01983f2a7c317b02",
    )
    val refreshToken: String,

    @field:Schema(description = "accessToken 만료까지 남은 초", example = "3600")
    val accessTokenExpiresIn: Long,

    @get:Schema(description = "이번 로그인으로 새로 가입했는지 여부", example = "true")
    @get:JsonProperty("isNewUser")
    val isNewUser: Boolean,

    @field:Schema(description = "동의가 필요한 현재 버전 약관. 비어 있으면 바로 보드로 진입")
    val pendingTerms: List<PendingTermResponse>,
) {
    companion object {
        fun from(result: LoginResult): LoginResponse =
            LoginResponse(
                accessToken = result.tokenPair.accessToken,
                refreshToken = result.tokenPair.refreshToken,
                accessTokenExpiresIn = result.tokenPair.accessTokenExpiresIn,
                isNewUser = result.isNewUser,
                pendingTerms = result.pendingTerms.map(PendingTermResponse::from),
            )
    }
}
