package com.github.nexters.ppotto.auth.domain

import com.github.nexters.ppotto.global.oauth.OAuthProvider

data class SocialProfile(
    val provider: OAuthProvider,
    val providerUserId: String,
    val email: String?,
    val name: String?,
    val providerRefreshToken: String? = null,
    val authorizationCodeExchangeFailed: Boolean = false,
)
