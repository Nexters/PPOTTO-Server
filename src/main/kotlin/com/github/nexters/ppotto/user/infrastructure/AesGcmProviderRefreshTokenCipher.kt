package com.github.nexters.ppotto.user.infrastructure

import com.github.nexters.ppotto.user.domain.EncryptedProviderRefreshToken
import com.github.nexters.ppotto.user.infrastructure.config.ProviderRefreshTokenEncryptionProperties
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets.UTF_8
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Component
class AesGcmProviderRefreshTokenCipher(
    properties: ProviderRefreshTokenEncryptionProperties,
) {
    private val key: SecretKeySpec
    private val secureRandom = SecureRandom()

    init {
        val keyBytes = Base64.getDecoder().decode(properties.keyBase64)
        require(keyBytes.size == KEY_SIZE_BYTES) { "제공자 refresh token 암호화 키는 ${KEY_SIZE_BYTES}바이트여야 합니다." }
        key = SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(plaintext: String): EncryptedProviderRefreshToken {
        require(plaintext.isNotBlank()) { "암호화할 제공자 refresh token이 비어 있습니다." }

        val initializationVector = ByteArray(IV_SIZE_BYTES)
        secureRandom.nextBytes(initializationVector)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, initializationVector))
        cipher.updateAAD(ADDITIONAL_AUTHENTICATED_DATA)

        val payload = initializationVector + cipher.doFinal(plaintext.toByteArray(UTF_8))
        return EncryptedProviderRefreshToken("$FORMAT_PREFIX.${ENCODER.encodeToString(payload)}")
    }

    fun decrypt(encrypted: EncryptedProviderRefreshToken): String {
        val parts = encrypted.value.split('.', limit = 2)
        require(parts.size == 2 && parts[0] == FORMAT_PREFIX) { "지원하지 않는 제공자 refresh token 형식입니다." }

        val payload = DECODER.decode(parts[1])
        require(payload.size > IV_SIZE_BYTES) { "제공자 refresh token 암호문이 잘렸습니다." }

        val initializationVector = payload.copyOfRange(0, IV_SIZE_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, initializationVector))
        cipher.updateAAD(ADDITIONAL_AUTHENTICATED_DATA)

        return cipher.doFinal(payload.copyOfRange(IV_SIZE_BYTES, payload.size)).toString(UTF_8)
    }

    companion object {
        private const val FORMAT_PREFIX = "v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BYTES = 32
        private const val IV_SIZE_BYTES = 12
        private const val TAG_SIZE_BITS = 128

        private val ADDITIONAL_AUTHENTICATED_DATA = "ppotto-provider-refresh-token".toByteArray(UTF_8)
        private val ENCODER = Base64.getUrlEncoder().withoutPadding()
        private val DECODER = Base64.getUrlDecoder()
    }
}
