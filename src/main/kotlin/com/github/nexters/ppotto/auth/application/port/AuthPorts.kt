package com.github.nexters.ppotto.auth.application.port

import com.github.nexters.ppotto.auth.domain.AuthUser
import com.github.nexters.ppotto.auth.domain.LoginCommand
import com.github.nexters.ppotto.auth.domain.OAuthProvider
import com.github.nexters.ppotto.auth.domain.PendingTerm
import com.github.nexters.ppotto.auth.domain.SocialProfile
import com.github.nexters.ppotto.auth.domain.TokenPair
import com.github.nexters.ppotto.global.identifier.UserId

interface OAuthClient {
    val provider: OAuthProvider

    fun authenticate(command: LoginCommand): SocialProfile

    fun revoke(providerRefreshToken: String)
}

interface TokenProvider {
    fun issue(userId: UserId): TokenPair

    fun verifyAccessToken(accessToken: String): UserId
}

interface RefreshTokenStore {
    fun save(
        userId: UserId,
        refreshToken: String,
    )

    fun findUserId(refreshToken: String): UserId?

    fun rotate(
        userId: UserId,
        currentRefreshToken: String,
        newRefreshToken: String,
    ): Boolean

    fun delete(userId: UserId)
}

fun interface AuthUserPort {
    fun findOrCreate(profile: SocialProfile): AuthUser?
}

fun interface AuthTermsPort {
    fun findPendingTerms(userId: UserId): List<PendingTerm>
}

fun interface AuthActiveUserPort {
    fun isActive(userId: UserId): Boolean
}
