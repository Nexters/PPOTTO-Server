package com.github.nexters.ppotto.auth.application.port

import com.github.nexters.ppotto.auth.domain.AuthUser
import com.github.nexters.ppotto.auth.domain.LoginCommand
import com.github.nexters.ppotto.auth.domain.OAuthProvider
import com.github.nexters.ppotto.auth.domain.PendingTerm
import com.github.nexters.ppotto.auth.domain.SocialProfile
import com.github.nexters.ppotto.auth.domain.TokenPair
import java.util.UUID

interface OAuthClient {
    val provider: OAuthProvider

    fun authenticate(command: LoginCommand): SocialProfile

    fun revoke(providerRefreshToken: String)
}

interface TokenProvider {
    fun issue(userId: UUID): TokenPair

    fun verifyAccessToken(accessToken: String): UUID
}

interface RefreshTokenStore {
    fun save(
        userId: UUID,
        refreshToken: String,
    )

    fun findUserId(refreshToken: String): UUID?

    fun rotate(
        userId: UUID,
        currentRefreshToken: String,
        newRefreshToken: String,
    ): Boolean

    fun delete(userId: UUID)
}

fun interface AuthUserPort {
    fun findOrCreate(profile: SocialProfile): AuthUser
}

fun interface AuthTermsPort {
    fun findPendingTerms(userId: UUID): List<PendingTerm>
}

fun interface AuthActiveUserPort {
    fun isActive(userId: UUID): Boolean
}
