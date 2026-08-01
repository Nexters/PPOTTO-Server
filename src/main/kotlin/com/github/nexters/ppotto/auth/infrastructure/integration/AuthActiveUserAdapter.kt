package com.github.nexters.ppotto.auth.infrastructure.integration

import com.github.nexters.ppotto.auth.application.port.AuthActiveUserPort
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.user.application.UserService
import org.springframework.stereotype.Component

@Component
class AuthActiveUserAdapter(
    private val userService: UserService,
) : AuthActiveUserPort {
    override fun isActive(userId: UserId): Boolean = userService.isActive(userId)
}
