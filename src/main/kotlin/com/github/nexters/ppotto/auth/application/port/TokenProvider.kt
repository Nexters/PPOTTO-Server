package com.github.nexters.ppotto.auth.application.port

import com.github.nexters.ppotto.auth.domain.TokenPair
import com.github.nexters.ppotto.global.identifier.UserId

interface TokenProvider {
    fun issue(userId: UserId): TokenPair

    fun verifyAccessToken(accessToken: String): UserId
}
