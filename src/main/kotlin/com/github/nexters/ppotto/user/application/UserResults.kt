package com.github.nexters.ppotto.user.application

import com.github.nexters.ppotto.user.domain.OAuthProvider
import com.github.nexters.ppotto.user.domain.User

data class SocialUserCommand(
    val provider: OAuthProvider,
    val providerUserId: String,
    val email: String,
    val name: String?,
    val providerRefreshToken: String?,
) {
    init {
        listOf(
            providerUserId.isNotBlank(),
            email.isNotBlank(),
            name == null || name.isNotBlank(),
            providerRefreshToken == null || providerRefreshToken.isNotBlank(),
        ).all { it }
            .let { require(it) }
    }
}

data class UserRegistrationResult(
    val user: User,
    val isNewUser: Boolean,
)
