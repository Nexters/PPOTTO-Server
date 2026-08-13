package com.github.nexters.ppotto.auth.domain

import com.github.nexters.ppotto.global.identifier.TermId
import com.github.nexters.ppotto.global.identifier.UserId

data class SocialProfile(
    val provider: OAuthProvider,
    val providerUserId: String,
    val email: String?,
    val name: String?,
    val providerRefreshToken: String? = null,
    val authorizationCodeExchangeFailed: Boolean = false,
)

data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresIn: Long,
)

data class AuthUser(
    val userId: UserId,
    val isNewUser: Boolean,
)

data class PendingTerm(
    val id: TermId,
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
