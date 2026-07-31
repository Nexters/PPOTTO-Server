package com.github.nexters.ppotto.user.domain

@JvmInline
value class EncryptedProviderRefreshToken(
    val value: String,
) {
    init {
        value
            .isNotBlank()
            .let { require(it) }
    }
}
