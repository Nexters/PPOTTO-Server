package com.github.nexters.ppotto.user.presentation.dto

import com.github.nexters.ppotto.user.domain.OAuthProvider
import com.github.nexters.ppotto.user.domain.User
import java.time.Instant
import java.util.UUID

data class UserResponse(
    val id: UUID,
    val provider: OAuthProvider,
    val email: String,
    val createdAt: Instant,
) {
    companion object {
        fun from(user: User): UserResponse =
            UserResponse(
                id = user.id,
                provider = user.provider,
                email = user.email,
                createdAt = user.createdAt,
            )
    }
}
