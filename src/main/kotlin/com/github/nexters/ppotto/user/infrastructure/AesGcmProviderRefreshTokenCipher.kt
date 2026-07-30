package com.github.nexters.ppotto.user.infrastructure

import com.github.nexters.ppotto.user.application.port.ProviderRefreshTokenCipher
import com.github.nexters.ppotto.user.domain.EncryptedProviderRefreshToken
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
) : ProviderRefreshTokenCipher {
    private val key =
        Base64
            .getDecoder()
            .decode(properties.keyBase64)
            .also { require(it.size == KEY_SIZE_BYTES) }
            .let { SecretKeySpec(it, "AES") }
    private val secureRandom = SecureRandom()

    override fun encrypt(plaintext: String): EncryptedProviderRefreshToken {
        require(plaintext.isNotBlank())
        val initializationVector = ByteArray(IV_SIZE_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, initializationVector))
        cipher.updateAAD(ADDITIONAL_AUTHENTICATED_DATA)
        val ciphertext = cipher.doFinal(plaintext.toByteArray(UTF_8))
        val payload = initializationVector + ciphertext
        return EncryptedProviderRefreshToken("$FORMAT_PREFIX.${ENCODER.encodeToString(payload)}")
    }

    override fun decrypt(encrypted: EncryptedProviderRefreshToken): String {
        val parts = encrypted.value.split('.', limit = 2)
        require(parts.size == 2 && parts[0] == FORMAT_PREFIX)
        val payload = DECODER.decode(parts[1])
        require(payload.size > IV_SIZE_BYTES)
        val initializationVector = payload.copyOfRange(0, IV_SIZE_BYTES)
        val ciphertext = payload.copyOfRange(IV_SIZE_BYTES, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, initializationVector))
        cipher.updateAAD(ADDITIONAL_AUTHENTICATED_DATA)
        return cipher.doFinal(ciphertext).toString(UTF_8)
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
