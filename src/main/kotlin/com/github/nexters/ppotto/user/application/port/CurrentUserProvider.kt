package com.github.nexters.ppotto.user.application.port

import java.util.UUID

fun interface CurrentUserProvider {
    fun currentUserId(): UUID
}
