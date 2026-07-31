package com.github.nexters.ppotto.user.domain

import java.time.Instant
import java.util.UUID

data class User(
    val id: UUID,
    val provider: OAuthProvider,
    val providerUserId: String,
    val email: String,
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
                providerRefreshToken = null,
                updatedAt = at,
                deletedAt = at,
            )
}
