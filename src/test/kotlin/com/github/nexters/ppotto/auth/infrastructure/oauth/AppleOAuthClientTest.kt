package com.github.nexters.ppotto.auth.infrastructure.oauth

import com.github.nexters.ppotto.auth.config.AppleAuthProperties
import com.github.nexters.ppotto.auth.domain.AuthErrorCode
import com.github.nexters.ppotto.auth.domain.LoginCommand
import com.github.nexters.ppotto.auth.infrastructure.sha256Hex
import com.github.nexters.ppotto.global.error.UnauthorizedException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpServer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import java.net.InetSocketAddress
import java.net.URI
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.concurrent.atomic.AtomicReference

private const val REFRESH_TOKEN_ONLY_RESPONSE = """{"refresh_token":"apple-refresh-token"}"""

class AppleOAuthClientTest :
    BehaviorSpec({
        val keyPair =
            KeyPairGenerator
                .getInstance("RSA")
                .apply { initialize(2048) }
                .generateKeyPair()
        val publicKey = keyPair.public as RSAPublicKey
        val privateKey = keyPair.private as RSAPrivateKey
        val keyId = "apple-test-key"
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val modulus =
            encoder.encodeToString(
                publicKey.modulus
                    .toByteArray()
                    .dropLeadingZero(),
            )
        val exponent =
            encoder.encodeToString(
                publicKey.publicExponent
                    .toByteArray()
                    .dropLeadingZero(),
            )
        val jwks = """{"keys":[{"kty":"RSA","kid":"$keyId","alg":"RS256","n":"$modulus","e":"$exponent"}]}"""
        val revokeBody = AtomicReference("")
        val revokeStatus = AtomicReference(200)
        val tokenResponse = AtomicReference(REFRESH_TOKEN_ONLY_RESPONSE)
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/keys") { exchange -> exchange.respond(200, jwks) }
        server.createContext("/token") { exchange -> exchange.respond(200, tokenResponse.get()) }
        server.createContext("/revoke") { exchange ->
            revokeBody.set(
                exchange.requestBody
                    .bufferedReader()
                    .readText(),
            )
            exchange.respond(revokeStatus.get(), "")
        }
        server.start()

        val baseUri = "http://localhost:${server.address.port}"
        val properties =
            AppleAuthProperties(
                clientId = "com.nexters.ppotto",
                teamId = "TESTTEAM01",
                keyId = "TESTKEY001",
                privateKeyPath = "./src/test/resources/dummy-apple-key.p8",
                issuer = "https://appleid.apple.com",
                jwksUri = URI("$baseUri/keys"),
                tokenUri = URI("$baseUri/token"),
                revokeUri = URI("$baseUri/revoke"),
                clientSecretExpirationDays = 180,
                jwksCacheSeconds = 3600,
            )
        val appleOAuthApi =
            HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(RestClient.builder().build()))
                .build()
                .createClient(AppleOAuthApi::class.java)
        val client =
            AppleOAuthClient(
                appleOAuthApi,
                properties,
                AppleClientSecretGenerator(properties),
                AppleJwkProvider(appleOAuthApi, properties),
            )

        fun sign(claims: JWTClaimsSet): String {
            val jwt =
                SignedJWT(
                    JWSHeader
                        .Builder(JWSAlgorithm.RS256)
                        .keyID(keyId)
                        .build(),
                    claims,
                )
            jwt.sign(RSASSASigner(privateKey))
            return jwt.serialize()
        }

        fun identityToken(
            rawNonce: String,
            email: String? = "relay@privaterelay.appleid.com",
        ): String {
            val now = Instant.now()
            return JWTClaimsSet
                .Builder()
                .issuer(properties.issuer)
                .audience(properties.clientId)
                .subject("apple-user-id")
                .claim("nonce", rawNonce.sha256Hex())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .apply { email?.let { claim("email", it) } }
                .build()
                .let(::sign)
        }

        fun exchangeTokenResponse(
            subject: String = "apple-user-id",
            email: String? = null,
        ): String {
            if (email == null) return REFRESH_TOKEN_ONLY_RESPONSE
            val idToken =
                JWTClaimsSet
                    .Builder()
                    .issuer(properties.issuer)
                    .audience(properties.clientId)
                    .subject(subject)
                    .claim("email", email)
                    .build()
                    .let(::sign)
            return """{"refresh_token":"apple-refresh-token","id_token":"$idToken"}"""
        }

        afterSpec {
            server.stop(0)
        }

        Given("유효한 애플 identity token과 authorization code가 주어졌을 때") {
            revokeStatus.set(200)
            tokenResponse.set(REFRESH_TOKEN_ONLY_RESPONSE)
            val rawNonce = "raw-nonce"

            When("애플 로그인을 검증하면") {
                val profile =
                    client.authenticate(
                        LoginCommand.Apple(identityToken(rawNonce), "authorization-code", rawNonce, "뽀또"),
                    )

                Then("sub, 이메일, revoke용 refresh token을 반환한다") {
                    profile.providerUserId shouldBe "apple-user-id"
                    profile.email shouldBe "relay@privaterelay.appleid.com"
                    profile.name shouldBe "뽀또"
                    profile.providerRefreshToken shouldBe "apple-refresh-token"
                    profile.authorizationCodeExchangeFailed.shouldBeFalse()
                }
            }

            When("보관한 provider refresh token을 폐기하면") {
                client.revoke("apple-refresh-token")

                Then("애플 revoke API에 refresh token을 전달한다") {
                    revokeBody.get() shouldContain "token=apple-refresh-token"
                }
            }
        }

        Given("이미 해지된 토큰이라 애플이 400을 반환할 때") {
            revokeStatus.set(400)

            When("계정 해지를 요청하면") {
                client.revoke("already-revoked-token")

                Then("예외 없이 통과해 탈퇴를 계속할 수 있다") {
                    revokeBody.get() shouldContain "token=already-revoked-token"
                }
            }
        }

        Given("애플 장애로 revoke가 500을 반환할 때") {
            revokeStatus.set(500)

            When("계정 해지를 요청하면") {
                val exception = shouldThrow<RestClientException> { client.revoke("apple-refresh-token") }

                Then("예외를 전파해 탈퇴를 중단시킨다") {
                    exception.message shouldContain "500"
                }
            }
        }

        Given("재로그인이라 identity token에 email이 없고 code 교환 응답이 이메일을 담고 있을 때") {
            revokeStatus.set(200)
            tokenResponse.set(exchangeTokenResponse(email = "relay@privaterelay.appleid.com"))
            val rawNonce = "relogin-nonce"

            When("애플 로그인을 검증하면") {
                val profile =
                    client.authenticate(
                        LoginCommand.Apple(identityToken(rawNonce, null), "authorization-code", rawNonce, null),
                    )

                Then("교환 id_token에서 이메일을 확보한다") {
                    profile.providerUserId shouldBe "apple-user-id"
                    profile.email shouldBe "relay@privaterelay.appleid.com"
                }
            }
        }

        Given("재로그인이라 identity token에도 code 교환 응답에도 이메일이 없을 때") {
            revokeStatus.set(200)
            tokenResponse.set(exchangeTokenResponse())
            val rawNonce = "relogin-nonce"

            When("애플 로그인을 검증하면") {
                val profile =
                    client.authenticate(
                        LoginCommand.Apple(identityToken(rawNonce, null), "authorization-code", rawNonce, null),
                    )

                Then("이메일 없이 프로필을 반환한다") {
                    profile.providerUserId shouldBe "apple-user-id"
                    profile.email.shouldBeNull()
                    profile.authorizationCodeExchangeFailed.shouldBeFalse()
                }
            }
        }

        Given("code 교환 id_token의 sub가 identity token과 다를 때") {
            revokeStatus.set(200)
            tokenResponse.set(exchangeTokenResponse("other-apple-user", "attacker@example.com"))
            val rawNonce = "relogin-nonce"

            When("애플 로그인을 검증하면") {
                val profile =
                    client.authenticate(
                        LoginCommand.Apple(identityToken(rawNonce, null), "authorization-code", rawNonce, null),
                    )

                Then("해당 이메일을 사용하지 않는다") {
                    profile.email.shouldBeNull()
                }
            }
        }

        Given("raw nonce가 identity token의 nonce와 다를 때") {
            revokeStatus.set(200)
            tokenResponse.set(REFRESH_TOKEN_ONLY_RESPONSE)

            When("애플 로그인을 검증하면") {
                val exception =
                    shouldThrow<UnauthorizedException> {
                        client.authenticate(
                            LoginCommand.Apple(identityToken("original"), "authorization-code", "different", null),
                        )
                    }

                Then("AUTH-001 예외가 발생한다") {
                    exception.errorCode shouldBe AuthErrorCode.SOCIAL_AUTHENTICATION_FAILED
                }
            }
        }
    }) {
    companion object {
        private fun ByteArray.dropLeadingZero(): ByteArray = if (size > 1 && first() == 0.toByte()) copyOfRange(1, size) else this
    }
}
