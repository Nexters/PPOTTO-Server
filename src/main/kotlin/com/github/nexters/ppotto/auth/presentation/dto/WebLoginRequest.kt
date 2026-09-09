package com.github.nexters.ppotto.auth.presentation.dto

import com.github.nexters.ppotto.auth.domain.LoginCommand
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.oauth.OAuthProvider
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

@Schema(description = "웹 소셜 로그인 요청")
data class WebLoginRequest(
    @field:NotNull
    @field:Schema(description = "소셜 로그인 제공자. 현재 KAKAO만 지원", example = "KAKAO")
    val provider: OAuthProvider?,

    @field:NotBlank
    @field:Schema(
        description = "provider 인가 페이지가 redirect URI로 돌려준 authorization code. 1회, 수 분 안에만 유효",
        example = "sample-kakao-authorization-code",
    )
    val authorizationCode: String,

    @field:NotBlank
    @field:Schema(
        description = "인가 요청에 사용한 redirect URI. provider 콘솔에 등록된 값과 정확히 같아야 함",
        example = "https://ppotto.co.kr/oauth/kakao",
    )
    val redirectUri: String,
) {
    fun toCommand(): LoginCommand =
        when (provider) {
            OAuthProvider.KAKAO ->
                LoginCommand.KakaoWeb(
                    authorizationCode = authorizationCode,
                    redirectUri = redirectUri,
                )
            OAuthProvider.APPLE, null -> throw InvalidInputException()
        }
}
