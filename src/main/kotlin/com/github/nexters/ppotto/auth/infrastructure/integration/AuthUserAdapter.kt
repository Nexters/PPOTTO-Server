package com.github.nexters.ppotto.auth.infrastructure.integration

import com.github.nexters.ppotto.auth.application.port.AuthUserPort
import com.github.nexters.ppotto.auth.domain.AuthUser
import com.github.nexters.ppotto.auth.domain.OAuthProvider
import com.github.nexters.ppotto.auth.domain.SocialProfile
import com.github.nexters.ppotto.board.application.BoardCommandService
import com.github.nexters.ppotto.user.application.SocialUserCommand
import com.github.nexters.ppotto.user.application.UserRegistrationResult
import com.github.nexters.ppotto.user.application.UserService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import com.github.nexters.ppotto.user.domain.OAuthProvider as UserOAuthProvider

@Component
class AuthUserAdapter(
    private val userService: UserService,
    private val boardCommandService: BoardCommandService,
) : AuthUserPort {
    @Transactional
    override fun findOrCreate(profile: SocialProfile): AuthUser =
        userService
            .findOrCreate(profile.toCommand())
            .also {
                it
                    .takeIf(UserRegistrationResult::isNewUser)
                    ?.let { newUser -> boardCommandService.createDefault(newUser.user.id) }
            }.toAuthUser()

    private fun SocialProfile.toCommand() =
        SocialUserCommand(
            provider = provider.toUserProvider(),
            providerUserId = providerUserId,
            email = email,
            providerRefreshToken = providerRefreshToken,
        )

    private fun OAuthProvider.toUserProvider(): UserOAuthProvider =
        when (this) {
            OAuthProvider.KAKAO -> UserOAuthProvider.KAKAO
            OAuthProvider.APPLE -> UserOAuthProvider.APPLE
        }

    private fun UserRegistrationResult.toAuthUser() =
        AuthUser(
            userId = user.id,
            isNewUser = isNewUser,
        )
}
