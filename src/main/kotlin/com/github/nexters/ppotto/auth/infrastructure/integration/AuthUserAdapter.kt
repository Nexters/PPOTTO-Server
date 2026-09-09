package com.github.nexters.ppotto.auth.infrastructure.integration

import com.github.nexters.ppotto.auth.application.port.AuthUserPort
import com.github.nexters.ppotto.auth.domain.AuthUser
import com.github.nexters.ppotto.auth.domain.SocialProfile
import com.github.nexters.ppotto.board.application.BoardCommandService
import com.github.nexters.ppotto.user.application.SocialUserCommand
import com.github.nexters.ppotto.user.application.UserRegistrationResult
import com.github.nexters.ppotto.user.application.UserService
import org.springframework.stereotype.Component

@Component
class AuthUserAdapter(
    private val userService: UserService,
    private val boardCommandService: BoardCommandService,
) : AuthUserPort {
    override fun findOrCreate(profile: SocialProfile): AuthUser? {
        val registration = userService.findOrCreate(profile.toCommand()) ?: return null
        if (registration.isNewUser) {
            boardCommandService.createDefault(registration.user.id)
        }
        return registration.toAuthUser()
    }

    private fun SocialProfile.toCommand() =
        SocialUserCommand(
            provider = provider,
            providerUserId = providerUserId,
            email = email,
            name = name,
            providerRefreshToken = providerRefreshToken,
        )

    private fun UserRegistrationResult.toAuthUser() =
        AuthUser(
            userId = user.id,
            isNewUser = isNewUser,
        )
}
