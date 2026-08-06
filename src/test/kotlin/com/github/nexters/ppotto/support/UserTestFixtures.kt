package com.github.nexters.ppotto.support

import com.github.nexters.ppotto.user.domain.OAuthProvider
import com.github.nexters.ppotto.user.domain.User
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import java.util.UUID

fun UserRepository.saveTestUser(): User =
    UUID.randomUUID().let {
        save(
            provider = OAuthProvider.KAKAO,
            providerUserId = "test-$it",
            email = "test-$it@example.com",
            name = "테스트사용자",
        )
    }
