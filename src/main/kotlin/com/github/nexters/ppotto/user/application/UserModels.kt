package com.github.nexters.ppotto.user.application

import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.oauth.OAuthProvider
import com.github.nexters.ppotto.user.domain.User

data class SocialUserCommand(
    val provider: OAuthProvider,
    val providerUserId: String,
    val email: String?,
    val name: String?,
    val providerRefreshToken: String?,
) {
    init {
        require(providerUserId.isNotBlank()) { "provider user id가 비어 있습니다." }
        require(email == null || email.isNotBlank()) { "이메일이 빈 문자열입니다." }
        require(name == null || name.isNotBlank()) { "이름이 빈 문자열입니다." }
        require(providerRefreshToken == null || providerRefreshToken.isNotBlank()) {
            "provider refresh token이 빈 문자열입니다."
        }
    }
}

data class UserRegistrationResult(
    val user: User,
    val isNewUser: Boolean,
)

data class WithdrawnUserCleanupResult(
    val attempted: Int,
    val deletedUserIds: List<UserId>,
)
