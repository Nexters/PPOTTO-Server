package com.github.nexters.ppotto.auth.infrastructure.oauth

import com.github.nexters.ppotto.auth.config.AppleAuthProperties
import com.github.nexters.ppotto.auth.domain.AuthErrorCode
import com.github.nexters.ppotto.auth.domain.LoginCommand
import com.github.nexters.ppotto.global.error.UnauthorizedException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpServer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.concurrent.atomic.AtomicReference

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
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/keys") { exchange ->
            exchange.respond(200, jwks)
        }
        server.createContext("/token") { exchange ->
            exchange.respond(200, """{"refresh_token":"apple-refresh-token"}""")
        }
        server.createContext("/revoke") { exchange ->
            revokeBody.set(
                exchange.requestBody
                    .bufferedReader()
                    .readText(),
            )
            exchange.respond(200, "")
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
        val client =
            AppleOAuthClient(
                HttpServiceProxyFactory
                    .builderFor(RestClientAdapter.create(RestClient.builder().build()))
                    .build()
                    .createClient(AppleOAuthApi::class.java),
                properties,
                AppleClientSecretGenerator(properties),
            )

        fun identityToken(rawNonce: String): String {
            val now = Instant.now()
            val claims =
                JWTClaimsSet
                    .Builder()
                    .issuer(properties.issuer)
                    .audience(properties.clientId)
                    .subject("apple-user-id")
                    .claim("email", "relay@privaterelay.appleid.com")
                    .claim("nonce", sha256(rawNonce))
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(300)))
                    .build()
            return SignedJWT(
                JWSHeader
                    .Builder(JWSAlgorithm.RS256)
                    .keyID(keyId)
                    .build(),
                claims,
            ).apply { sign(RSASSASigner(privateKey)) }
                .serialize()
        }

        afterSpec {
            server.stop(0)
        }

        Given("유효한 애플 identity token과 authorization code가 주어졌을 때") {
            val rawNonce = "raw-nonce"

            When("애플 로그인을 검증하면") {
                Then("sub, 이메일, revoke용 refresh token을 반환한다") {
                    val profile =
                        client.authenticate(
                            LoginCommand.Apple(identityToken(rawNonce), "authorization-code", rawNonce, "뽀또"),
                        )

                    profile.providerUserId shouldBe "apple-user-id"
                    profile.email shouldBe "relay@privaterelay.appleid.com"
                    profile.name shouldBe "뽀또"
                    profile.providerRefreshToken shouldBe "apple-refresh-token"
                    profile.authorizationCodeExchangeFailed shouldBe false
                }
            }

            When("보관한 provider refresh token을 폐기하면") {
                Then("애플 revoke API에 refresh token을 전달한다") {
                    client.revoke("apple-refresh-token")

                    revokeBody.get().contains("token=apple-refresh-token") shouldBe true
                }
            }
        }

        Given("raw nonce가 identity token의 nonce와 다를 때") {
            When("애플 로그인을 검증하면") {
                Then("AUTH-001 예외가 발생한다") {
                    val exception =
                        shouldThrow<UnauthorizedException> {
                            client.authenticate(
                                LoginCommand.Apple(identityToken("original"), "authorization-code", "different", null),
                            )
                        }
                    exception.errorCode shouldBe AuthErrorCode.SOCIAL_AUTHENTICATION_FAILED
                }
            }
        }
    }) {
    companion object {
        private fun ByteArray.dropLeadingZero(): ByteArray = if (size > 1 && first() == 0.toByte()) copyOfRange(1, size) else this

        private fun sha256(value: String): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest(value.toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        private fun com.sun.net.httpserver.HttpExchange.respond(
            status: Int,
            body: String,
        ) {
            responseHeaders.add("Content-Type", "application/json")
            val bytes = body.toByteArray()
            sendResponseHeaders(status, bytes.size.toLong())
            responseBody.use { it.write(bytes) }
        }
    }
}
