package com.github.nexters.ppotto.auth.infrastructure.integration

import com.github.nexters.ppotto.auth.application.port.RefreshTokenStore
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.user.application.port.UserSessionRevoker
import org.springframework.stereotype.Component

@Component
class RefreshTokenUserSessionRevoker(
    private val refreshTokenStore: RefreshTokenStore,
) : UserSessionRevoker {
    override fun revoke(userId: UserId): Unit = refreshTokenStore.delete(userId)
}
