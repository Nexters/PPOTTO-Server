package com.github.nexters.ppotto.auth.application.port

import com.github.nexters.ppotto.global.identifier.UserId

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
