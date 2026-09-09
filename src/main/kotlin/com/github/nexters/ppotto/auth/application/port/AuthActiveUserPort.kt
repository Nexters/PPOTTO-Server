package com.github.nexters.ppotto.auth.application.port

import com.github.nexters.ppotto.global.identifier.UserId

fun interface AuthActiveUserPort {
    fun isActive(userId: UserId): Boolean
}
