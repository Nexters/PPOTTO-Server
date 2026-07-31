package com.github.nexters.ppotto.auth.presentation.dto

import com.github.nexters.ppotto.auth.domain.LoginCommand
import com.github.nexters.ppotto.auth.domain.OAuthProvider
import com.github.nexters.ppotto.global.error.InvalidInputException
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

@Schema(description = "소셜 로그인 요청")
data class LoginRequest(
    @field:NotNull
    @field:Schema(description = "소셜 로그인 제공자", example = "KAKAO")
    val provider: OAuthProvider?,
    @field:Schema(description = "카카오 access token")
    val accessToken: String?,
    @field:Schema(description = "애플 identity token")
    val identityToken: String?,
    @field:Schema(description = "애플 authorization code")
    val authorizationCode: String?,
    @field:Schema(description = "애플 로그인 nonce 원문")
    val rawNonce: String?,
) {
    fun toCommand(): LoginCommand =
        when (provider) {
            OAuthProvider.KAKAO -> LoginCommand.Kakao(accessToken.required())
            OAuthProvider.APPLE ->
                LoginCommand.Apple(
                    identityToken.required(),
                    authorizationCode.required(),
                    rawNonce.required(),
                )
            null -> throw InvalidInputException()
        }

    private fun String?.required(): String = this?.takeIf(String::isNotBlank) ?: throw InvalidInputException()
}

@Schema(description = "토큰 재발급 요청")
data class RefreshRequest(
    @field:NotBlank
    @field:Schema(description = "로그인 또는 이전 재발급에서 받은 refresh token")
    val refreshToken: String,
)
