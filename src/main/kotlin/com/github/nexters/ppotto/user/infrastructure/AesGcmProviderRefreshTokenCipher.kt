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

    override fun encrypt(plaintext: String): EncryptedProviderRefreshToken =
        plaintext
            .also { require(it.isNotBlank()) }
            .let {
                ByteArray(IV_SIZE_BYTES)
                    .also(secureRandom::nextBytes)
                    .let { initializationVector ->
                        Cipher
                            .getInstance(TRANSFORMATION)
                            .apply {
                                init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, initializationVector))
                                updateAAD(ADDITIONAL_AUTHENTICATED_DATA)
                            }.doFinal(it.toByteArray(UTF_8))
                            .let(initializationVector::plus)
                    }
            }.let { EncryptedProviderRefreshToken("$FORMAT_PREFIX.${ENCODER.encodeToString(it)}") }

    override fun decrypt(encrypted: EncryptedProviderRefreshToken): String =
        encrypted
            .value
            .split('.', limit = 2)
            .also { require(it.size == 2 && it[0] == FORMAT_PREFIX) }
            .let { DECODER.decode(it[1]) }
            .also { require(it.size > IV_SIZE_BYTES) }
            .let { payload ->
                payload.copyOfRange(0, IV_SIZE_BYTES).let { initializationVector ->
                    Cipher
                        .getInstance(TRANSFORMATION)
                        .apply {
                            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, initializationVector))
                            updateAAD(ADDITIONAL_AUTHENTICATED_DATA)
                        }.doFinal(payload.copyOfRange(IV_SIZE_BYTES, payload.size))
                        .toString(UTF_8)
                }
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
