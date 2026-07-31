package com.github.nexters.ppotto.user.support

import com.github.nexters.ppotto.user.application.port.UserSessionRevoker
import java.util.UUID

class FakeUserSessionRevoker : UserSessionRevoker {
    val revokedUserIds = mutableListOf<UUID>()

    override fun revoke(userId: UUID) {
        revokedUserIds += userId
    }

    fun clear() {
        revokedUserIds.clear()
    }
}
