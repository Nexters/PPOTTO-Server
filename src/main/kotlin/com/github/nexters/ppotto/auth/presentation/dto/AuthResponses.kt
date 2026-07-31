package com.github.nexters.ppotto.auth.presentation.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.nexters.ppotto.auth.domain.LoginResult
import com.github.nexters.ppotto.auth.domain.PendingTerm
import com.github.nexters.ppotto.auth.domain.TokenPair
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

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
            TokenPairResponse(tokenPair.accessToken, tokenPair.refreshToken, tokenPair.accessTokenExpiresIn)
    }
}

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

@Schema(description = "로그인 후 동의가 필요한 약관")
data class PendingTermResponse(
    @field:Schema(description = "약관 ID (uuidv7)", example = "01983f2a-1a2b-7c3d-8e4f-5a6b7c8d9e0f")
    val id: UUID,
    @field:Schema(description = "약관 코드", example = "TOS")
    val code: String,
    @field:Schema(description = "약관 버전", example = "1.0")
    val version: String,
    @get:Schema(description = "필수 동의 여부", example = "true")
    @get:JsonProperty("isRequired")
    val isRequired: Boolean,
    @field:Schema(description = "노션 등 외부 문서 링크", example = "https://nexters.notion.site/ppotto-tos")
    val contentUrl: String?,
    @field:Schema(description = "요청 사용자의 동의 여부", example = "false")
    val agreed: Boolean,
) {
    companion object {
        fun from(term: PendingTerm): PendingTermResponse =
            PendingTermResponse(term.id, term.code, term.version, term.isRequired, term.contentUrl, term.agreed)
    }
}
