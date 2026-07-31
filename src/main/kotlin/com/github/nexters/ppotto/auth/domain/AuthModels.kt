package com.github.nexters.ppotto.auth.domain

import java.util.UUID

data class SocialProfile(
    val provider: OAuthProvider,
    val providerUserId: String,
    val email: String,
    val providerRefreshToken: String? = null,
    val authorizationCodeExchangeFailed: Boolean = false,
)

data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresIn: Long,
)

data class AuthUser(
    val userId: UUID,
    val isNewUser: Boolean,
)

data class PendingTerm(
    val id: UUID,
    val code: String,
    val version: String,
    val isRequired: Boolean,
    val contentUrl: String?,
    val agreed: Boolean,
)

data class AuthSignup(
    val user: AuthUser,
    val pendingTerms: List<PendingTerm>,
)

data class LoginResult(
    val tokenPair: TokenPair,
    val isNewUser: Boolean,
    val pendingTerms: List<PendingTerm>,
)
