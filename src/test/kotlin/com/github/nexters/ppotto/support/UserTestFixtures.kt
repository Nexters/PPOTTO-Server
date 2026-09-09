package com.github.nexters.ppotto.support

import com.github.nexters.ppotto.global.oauth.OAuthProvider
import com.github.nexters.ppotto.user.domain.User
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import java.util.UUID

fun UserRepository.saveTestUser(): User {
    val unique = UUID.randomUUID()
    return save(
        provider = OAuthProvider.KAKAO,
        providerUserId = "test-$unique",
        email = "test-$unique@example.com",
        name = "테스트사용자",
    )
}
