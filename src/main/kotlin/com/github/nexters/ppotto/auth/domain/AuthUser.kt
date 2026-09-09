package com.github.nexters.ppotto.auth.domain

import com.github.nexters.ppotto.global.identifier.UserId

data class AuthUser(
    val userId: UserId,
    val isNewUser: Boolean,
)
