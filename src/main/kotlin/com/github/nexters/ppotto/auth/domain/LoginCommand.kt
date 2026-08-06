package com.github.nexters.ppotto.auth.domain

sealed interface LoginCommand {
    val provider: OAuthProvider

    data class Kakao(
        val accessToken: String,
    ) : LoginCommand {
        override val provider = OAuthProvider.KAKAO
    }

    data class Apple(
        val identityToken: String,
        val authorizationCode: String,
        val rawNonce: String,
        val name: String?,
    ) : LoginCommand {
        override val provider = OAuthProvider.APPLE
    }
}
