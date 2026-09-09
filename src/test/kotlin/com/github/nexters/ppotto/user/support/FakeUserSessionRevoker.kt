package com.github.nexters.ppotto.user.support

import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.support.ResettableFake
import com.github.nexters.ppotto.user.application.port.UserSessionRevoker
import java.util.concurrent.CopyOnWriteArrayList

class FakeUserSessionRevoker :
    UserSessionRevoker,
    ResettableFake {
    val revokedUserIds = CopyOnWriteArrayList<UserId>()

    override fun revoke(userId: UserId) {
        revokedUserIds += userId
    }

    override fun reset() {
        revokedUserIds.clear()
    }
}
