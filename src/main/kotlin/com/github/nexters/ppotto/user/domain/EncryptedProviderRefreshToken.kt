package com.github.nexters.ppotto.user.domain

@JvmInline
value class EncryptedProviderRefreshToken(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "암호화된 제공자 refresh token이 비어 있습니다." }
    }
}
