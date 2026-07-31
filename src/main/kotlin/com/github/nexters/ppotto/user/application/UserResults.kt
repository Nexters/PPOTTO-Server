package com.github.nexters.ppotto.user.application

import com.github.nexters.ppotto.user.domain.OAuthProvider
import com.github.nexters.ppotto.user.domain.User

data class SocialUserCommand(
    val provider: OAuthProvider,
    val providerUserId: String,
    val email: String,
    val providerRefreshToken: String?,
) {
    init {
        require(providerUserId.isNotBlank())
        require(email.isNotBlank())
        require(providerRefreshToken == null || providerRefreshToken.isNotBlank())
    }
}

data class UserRegistrationResult(
    val user: User,
    val isNewUser: Boolean,
)
