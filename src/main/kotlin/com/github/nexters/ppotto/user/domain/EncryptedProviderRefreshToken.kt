package com.github.nexters.ppotto.user.domain

@JvmInline
value class EncryptedProviderRefreshToken(
    val value: String,
) {
    init {
        require(value.isNotBlank())
    }
}
