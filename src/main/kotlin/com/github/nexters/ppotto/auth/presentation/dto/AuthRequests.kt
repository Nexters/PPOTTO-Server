package com.github.nexters.ppotto.auth.presentation.dto

import com.github.nexters.ppotto.auth.domain.LoginCommand
import com.github.nexters.ppotto.auth.domain.OAuthProvider
import com.github.nexters.ppotto.global.error.InvalidInputException
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class LoginRequest(
    @field:NotNull
    val provider: OAuthProvider?,
    val accessToken: String?,
    val identityToken: String?,
    val authorizationCode: String?,
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

data class RefreshRequest(
    @field:NotBlank
    val refreshToken: String,
)
