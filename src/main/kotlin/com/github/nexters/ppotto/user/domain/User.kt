package com.github.nexters.ppotto.user.domain

import com.github.nexters.ppotto.global.identifier.UserId
import java.time.Instant

data class User(
    val id: UserId,
    val provider: OAuthProvider,
    val providerUserId: String,
    val email: String,
    val name: String,
    val providerRefreshToken: EncryptedProviderRefreshToken?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
) {
    val isActive: Boolean
        get() = deletedAt == null

    fun withdraw(at: Instant): User =
        this
            .also { require(it.isActive) }
            .copy(
                email = "deleted+$id@users.invalid",
                name = "탈퇴한 사용자",
                providerRefreshToken = null,
                updatedAt = at,
                deletedAt = at,
            )
}
