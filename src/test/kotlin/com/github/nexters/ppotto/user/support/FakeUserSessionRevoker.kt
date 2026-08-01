package com.github.nexters.ppotto.user.support

import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.user.application.port.UserSessionRevoker

class FakeUserSessionRevoker : UserSessionRevoker {
    val revokedUserIds = mutableListOf<UserId>()

    override fun revoke(userId: UserId) {
        revokedUserIds += userId
    }

    fun clear() {
        revokedUserIds.clear()
    }
}
