package com.github.nexters.ppotto.user.infrastructure

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.Base64

class AesGcmProviderRefreshTokenCipherTest :
    BehaviorSpec({
        val key = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
        val cipher =
            AesGcmProviderRefreshTokenCipher(
                ProviderRefreshTokenEncryptionProperties(key),
            )

        Given("제공자 refresh token 평문이 있을 때") {
            val plaintext = "provider-refresh-token"

            When("같은 값을 두 번 암호화하면") {
                val first = cipher.encrypt(plaintext)
                val second = cipher.encrypt(plaintext)

                Then("매번 다른 암호문을 만들고 모두 원문으로 복호화한다") {
                    first shouldNotBe second
                    cipher.decrypt(first) shouldBe plaintext
                    cipher.decrypt(second) shouldBe plaintext
                }
            }
        }
    })
