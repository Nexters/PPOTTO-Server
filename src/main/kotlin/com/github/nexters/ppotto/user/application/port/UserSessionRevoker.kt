package com.github.nexters.ppotto.user.application.port

import java.util.UUID

fun interface UserSessionRevoker {
    fun revoke(userId: UUID)
}
