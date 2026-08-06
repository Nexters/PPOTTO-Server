package com.github.nexters.ppotto.auth.presentation.dto

import com.github.nexters.ppotto.auth.domain.LoginCommand
import com.github.nexters.ppotto.auth.domain.OAuthProvider
import com.github.nexters.ppotto.global.error.InvalidInputException
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

@Schema(description = "소셜 로그인 요청")
data class LoginRequest(
    @field:NotNull
    @field:Schema(description = "소셜 로그인 제공자", example = "KAKAO")
    val provider: OAuthProvider?,

    @field:Schema(
        description = "카카오 SDK가 발급한 OAuth access token. provider=KAKAO일 때 필수",
        example = "v1.sample-kakao-oauth-access-token",
    )
    val accessToken: String?,

    @field:Schema(
        description = "애플 로그인 결과로 받은 identity token (JWT). provider=APPLE일 때 필수",
        example = "eyJhbGciOiJSUzI1NiJ9.sample-apple-identity-token.sample-signature",
    )
    val identityToken: String?,

    @field:Schema(
        description = "애플 refresh token 교환용 코드. 발급 후 5분, 1회만 유효하며 provider=APPLE일 때 필수",
        example = "sample.0.srtwx.apple-authorization-code",
    )
    val authorizationCode: String?,

    @field:Schema(
        description = "클라이언트가 생성한 원본 nonce. 애플에는 SHA-256 해시를, 서버에는 원본을 보냄",
        example = "4A7F0E2B-9C31-45D8-A6F2-8B0C3D9E1F52",
    )
    val rawNonce: String?,

    @field:Size(max = 100)
    @field:Schema(
        description =
            "사용자 이름. provider=APPLE 최초 인가에서 받은 fullName을 전달하며 신규 가입 시 필수, " +
                "재로그인 시 생략. provider=KAKAO는 서버가 닉네임을 직접 조회하므로 보내면 400",
        example = "뽀또",
    )
    val name: String?,
) {
    fun toCommand(): LoginCommand =
        when (provider) {
            OAuthProvider.KAKAO -> LoginCommand.Kakao(accessToken.required()).takeIf { name == null } ?: throw InvalidInputException()
            OAuthProvider.APPLE ->
                LoginCommand.Apple(
                    identityToken.required(),
                    authorizationCode.required(),
                    rawNonce.required(),
                    name?.let { it.takeIf(String::isNotBlank) ?: throw InvalidInputException() },
                )
            null -> throw InvalidInputException()
        }

    private fun String?.required(): String = this?.takeIf(String::isNotBlank) ?: throw InvalidInputException()
}

@Schema(description = "토큰 재발급 요청")
data class RefreshRequest(
    @field:NotBlank
    @field:Schema(
        description = "로그인 또는 이전 재발급에서 받은 refresh token",
        example = "sample-refresh-token-01983f2a7c317b02",
    )
    val refreshToken: String,
)
