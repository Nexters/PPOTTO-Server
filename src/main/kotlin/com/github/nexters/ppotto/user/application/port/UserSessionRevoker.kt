package com.github.nexters.ppotto.user.application.port

import com.github.nexters.ppotto.global.identifier.UserId

fun interface UserSessionRevoker {
    fun revoke(userId: UserId)
}
