package com.github.nexters.ppotto.user.application.port

import com.github.nexters.ppotto.user.domain.EncryptedProviderRefreshToken

interface ProviderRefreshTokenCipher {
    fun encrypt(plaintext: String): EncryptedProviderRefreshToken

    fun decrypt(encrypted: EncryptedProviderRefreshToken): String
}
