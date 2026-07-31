package com.github.nexters.ppotto.auth.infrastructure.integration

import com.github.nexters.ppotto.auth.application.port.RefreshTokenStore
import com.github.nexters.ppotto.user.application.port.UserSessionRevoker
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class RefreshTokenUserSessionRevoker(
    private val refreshTokenStore: RefreshTokenStore,
) : UserSessionRevoker {
    override fun revoke(userId: UUID): Unit = refreshTokenStore.delete(userId)
}
