package com.github.nexters.ppotto.auth.application.port

import com.github.nexters.ppotto.auth.domain.AuthUser
import com.github.nexters.ppotto.auth.domain.SocialProfile

fun interface AuthUserPort {
    fun findOrCreate(profile: SocialProfile): AuthUser?
}
