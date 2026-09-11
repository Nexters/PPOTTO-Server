package com.github.nexters.ppotto.auth.infrastructure.oauth

import com.github.nexters.ppotto.auth.domain.AuthErrorCode
import com.github.nexters.ppotto.auth.domain.LoginCommand
import com.github.nexters.ppotto.auth.infrastructure.config.AppleAuthProperties
import com.github.nexters.ppotto.auth.infrastructure.sha256Hex
import com.github.nexters.ppotto.global.error.UnauthorizedException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.web.client.RestClientException
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private const val REFRESH_TOKEN_ONLY_RESPONSE = """{"refresh_token":"apple-refresh-token"}"""
private const val PRIMARY_KEY_ID = "apple-test-key"
private const val ROTATED_KEY_ID = "apple-rotated-key"
private const val DEFAULT_JWKS_CACHE_SECONDS = 3_600L
private const val HS256_SECRET = "apple-forged-secret-that-is-at-least-32-bytes-long"
private val FUTURE = Instant.parse("2100-01-01T00:00:00Z")
private val PAST = Instant.parse("2020-01-01T00:00:00Z")
private val ISSUED_AT = Instant.parse("2026-01-01T00:00:00Z")

private data class AppleStub(
    val jwks: String,
    val jwksStatus: Int = 200,
    val tokenStatus: Int = 200,
    val tokenBody: String = REFRESH_TOKEN_ONLY_RESPONSE,
    val revokeStatus: Int = 200,
)

class AppleOAuthClientTest :
    BehaviorSpec({
        val primary = rsaKeyPair()
        val rotated = rsaKeyPair()
        val primaryJwks = jwksOf(PRIMARY_KEY_ID to primary.first)
        val rotatedJwks = jwksOf(ROTATED_KEY_ID to rotated.first)

        val stub = AtomicReference(AppleStub(jwks = primaryJwks))
        val lastRevokeBody = AtomicReference("")
        val jwksFetchCount = AtomicInteger()

        val server =
            stubOAuthServer {
                route("/keys") {
                    jwksFetchCount.incrementAndGet()
                    it.respond(stub.get().jwksStatus, stub.get().jwks)
                }
                route("/token") { it.respond(stub.get().tokenStatus, stub.get().tokenBody) }
                route("/revoke") { exchange ->
                    lastRevokeBody.set(
                        exchange.requestBody
                            .bufferedReader()
                            .readText(),
                    )
                    exchange.respond(stub.get().revokeStatus, "")
                }
            }

        val properties =
            AppleAuthProperties(
                clientId = "com.nexters.ppotto",
                teamId = "TESTTEAM01",
                keyId = "TESTKEY001",
                privateKeyPath = "./src/test/resources/dummy-apple-key.p8",
                issuer = "https://appleid.apple.com",
                jwksUri = server.uri("/keys"),
                tokenUri = server.uri("/token"),
                revokeUri = server.uri("/revoke"),
                clientSecretExpirationDays = 180,
                jwksCacheSeconds = DEFAULT_JWKS_CACHE_SECONDS,
            )
        val appleOAuthApi = server.api(AppleOAuthApi::class.java)

        fun appleClient(jwksCacheSeconds: Long = DEFAULT_JWKS_CACHE_SECONDS): AppleOAuthClient {
            val scoped = properties.copy(jwksCacheSeconds = jwksCacheSeconds)
            return AppleOAuthClient(
                appleOAuthApi,
                scoped,
                AppleClientSecretGenerator(scoped),
                AppleJwkProvider(appleOAuthApi, scoped),
            )
        }

        val client = appleClient()

        fun claims(
            rawNonce: String,
            issuer: String = properties.issuer,
            audience: String = properties.clientId,
            subject: String = "apple-user-id",
            expiresAt: Instant = FUTURE,
            email: String? = "relay@privaterelay.appleid.com",
        ): JWTClaimsSet =
            JWTClaimsSet
                .Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(subject)
                .claim("nonce", rawNonce.sha256Hex())
                .issueTime(Date.from(ISSUED_AT))
                .expirationTime(Date.from(expiresAt))
                .apply { email?.let { claim("email", it) } }
                .build()

        fun identityToken(
            claims: JWTClaimsSet,
            keyId: String? = PRIMARY_KEY_ID,
            privateKey: RSAPrivateKey = primary.second,
        ): String {
            val header =
                JWSHeader
                    .Builder(JWSAlgorithm.RS256)
                    .apply { keyId?.let(::keyID) }
                    .build()
            return SignedJWT(header, claims).apply { sign(RSASSASigner(privateKey)) }.serialize()
        }

        fun appleLogin(
            identityToken: String,
            rawNonce: String,
        ) = LoginCommand.Apple(identityToken, "authorization-code", rawNonce, null)

        fun exchangeTokenResponse(
            subject: String = "apple-user-id",
            email: String? = null,
        ): String {
            if (email == null) return REFRESH_TOKEN_ONLY_RESPONSE
            val idToken =
                identityToken(
                    JWTClaimsSet
                        .Builder()
                        .issuer(properties.issuer)
                        .audience(properties.clientId)
                        .subject(subject)
                        .claim("email", email)
                        .build(),
                )
            return """{"refresh_token":"apple-refresh-token","id_token":"$idToken"}"""
        }

        beforeTest { testCase ->
            if (testCase.parent == null) {
                stub.set(AppleStub(jwks = primaryJwks))
                lastRevokeBody.set("")
                jwksFetchCount.set(0)
            }
        }

        Given("유효한 애플 identity token과 authorization code가 주어졌을 때") {
            val rawNonce = "raw-nonce"

            When("애플 로그인을 검증하면") {
                val profile =
                    client.authenticate(
                        LoginCommand.Apple(identityToken(claims(rawNonce)), "authorization-code", rawNonce, "뽀또"),
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
                    lastRevokeBody.get() shouldContain "token=apple-refresh-token"
                }
            }
        }

        Given("이미 해지된 토큰이라 애플이 400을 반환할 때") {
            stub.updateAndGet { it.copy(revokeStatus = 400) }

            When("계정 해지를 요청하면") {
                val revoking = { client.revoke("already-revoked-token") }

                Then("예외 없이 통과해 탈퇴를 계속할 수 있다") {
                    shouldNotThrowAny(revoking)
                }

                Then("애플 revoke API에 해당 토큰을 전달한다") {
                    shouldNotThrowAny(revoking)
                    lastRevokeBody.get() shouldContain "token=already-revoked-token"
                }
            }
        }

        Given("애플 장애로 revoke가 500을 반환할 때") {
            stub.updateAndGet { it.copy(revokeStatus = 500) }

            When("계정 해지를 요청하면") {
                val exception = shouldThrow<RestClientException> { client.revoke("apple-refresh-token") }

                Then("예외를 전파해 탈퇴를 중단시킨다") {
                    exception.message shouldContain "500"
                }
            }
        }

        Given("재로그인이라 identity token에 email이 없고 code 교환 응답이 이메일을 담고 있을 때") {
            stub.updateAndGet { it.copy(tokenBody = exchangeTokenResponse(email = "relay@privaterelay.appleid.com")) }
            val rawNonce = "relogin-nonce"

            When("애플 로그인을 검증하면") {
                val profile = client.authenticate(appleLogin(identityToken(claims(rawNonce, email = null)), rawNonce))

                Then("교환 id_token에서 이메일을 확보한다") {
                    profile.providerUserId shouldBe "apple-user-id"
                    profile.email shouldBe "relay@privaterelay.appleid.com"
                }
            }
        }

        Given("재로그인이라 identity token에도 code 교환 응답에도 이메일이 없을 때") {
            val rawNonce = "no-email-nonce"

            When("애플 로그인을 검증하면") {
                val profile = client.authenticate(appleLogin(identityToken(claims(rawNonce, email = null)), rawNonce))

                Then("이메일 없이 프로필을 반환한다") {
                    profile.providerUserId shouldBe "apple-user-id"
                    profile.email.shouldBeNull()
                    profile.authorizationCodeExchangeFailed.shouldBeFalse()
                }
            }
        }

        Given("code 교환 id_token의 sub가 identity token과 다를 때") {
            stub.updateAndGet { it.copy(tokenBody = exchangeTokenResponse("other-apple-user", "attacker@example.com")) }
            val rawNonce = "other-sub-nonce"

            When("애플 로그인을 검증하면") {
                val profile = client.authenticate(appleLogin(identityToken(claims(rawNonce, email = null)), rawNonce))

                Then("해당 이메일을 사용하지 않는다") {
                    profile.email.shouldBeNull()
                }
            }
        }

        Given("authorization code가 만료되어 애플 token 교환이 400을 반환할 때") {
            stub.updateAndGet {
                it.copy(tokenStatus = 400, tokenBody = """{"error":"invalid_grant"}""")
            }
            val rawNonce = "exchange-failure-nonce"

            When("애플 로그인을 검증하면") {
                val profile = client.authenticate(appleLogin(identityToken(claims(rawNonce)), rawNonce))

                Then("교환 실패를 표시해 신규 가입을 AUTH-003으로 막게 한다") {
                    profile.authorizationCodeExchangeFailed.shouldBeTrue()
                }

                Then("보관할 provider refresh token이 없다") {
                    profile.providerRefreshToken.shouldBeNull()
                }
            }
        }

        Given("raw nonce가 identity token의 nonce와 다를 때") {
            When("애플 로그인을 검증하면") {
                val exception =
                    shouldThrow<UnauthorizedException> {
                        client.authenticate(appleLogin(identityToken(claims("original")), "different"))
                    }

                Then("AUTH-001 예외가 발생한다") {
                    exception.errorCode shouldBe AuthErrorCode.SOCIAL_AUTHENTICATION_FAILED
                }
            }
        }

        Given("issuer가 애플이 아닌 identity token이 주어졌을 때") {
            val rawNonce = "issuer-nonce"

            When("애플 로그인을 검증하면") {
                val exception =
                    shouldThrow<UnauthorizedException> {
                        client.authenticate(
                            appleLogin(identityToken(claims(rawNonce, issuer = "https://attacker.example.com")), rawNonce),
                        )
                    }

                Then("AUTH-001 예외가 발생한다") {
                    exception.errorCode shouldBe AuthErrorCode.SOCIAL_AUTHENTICATION_FAILED
                }
            }
        }

        Given("audience가 우리 client id가 아닌 identity token이 주어졌을 때") {
            val rawNonce = "audience-nonce"

            When("애플 로그인을 검증하면") {
                val exception =
                    shouldThrow<UnauthorizedException> {
                        client.authenticate(
                            appleLogin(identityToken(claims(rawNonce, audience = "com.attacker.app")), rawNonce),
                        )
                    }

                Then("AUTH-001 예외가 발생한다") {
                    exception.errorCode shouldBe AuthErrorCode.SOCIAL_AUTHENTICATION_FAILED
                }
            }
        }

        Given("만료 시각이 지난 identity token이 주어졌을 때") {
            val rawNonce = "expired-nonce"

            When("애플 로그인을 검증하면") {
                val exception =
                    shouldThrow<UnauthorizedException> {
                        client.authenticate(appleLogin(identityToken(claims(rawNonce, expiresAt = PAST)), rawNonce))
                    }

                Then("AUTH-001 예외가 발생한다") {
                    exception.errorCode shouldBe AuthErrorCode.SOCIAL_AUTHENTICATION_FAILED
                }
            }
        }

        Given("sub가 공백인 identity token이 주어졌을 때") {
            val rawNonce = "blank-subject-nonce"

            When("애플 로그인을 검증하면") {
                val exception =
                    shouldThrow<UnauthorizedException> {
                        client.authenticate(appleLogin(identityToken(claims(rawNonce, subject = " ")), rawNonce))
                    }

                Then("AUTH-001 예외가 발생한다") {
                    exception.errorCode shouldBe AuthErrorCode.SOCIAL_AUTHENTICATION_FAILED
                }
            }
        }

        Given("공개키 검증을 우회하려고 HS256으로 서명한 identity token이 주어졌을 때") {
            val rawNonce = "hs256-nonce"
            val forged =
                SignedJWT(
                    JWSHeader
                        .Builder(JWSAlgorithm.HS256)
                        .keyID(PRIMARY_KEY_ID)
                        .build(),
                    claims(rawNonce),
                ).apply { sign(MACSigner(HS256_SECRET.toByteArray())) }
                    .serialize()

            When("애플 로그인을 검증하면") {
                val exception =
                    shouldThrow<UnauthorizedException> { client.authenticate(appleLogin(forged, rawNonce)) }

                Then("AUTH-001 예외가 발생한다") {
                    exception.errorCode shouldBe AuthErrorCode.SOCIAL_AUTHENTICATION_FAILED
                }
            }
        }

        Given("kid 헤더가 없는 identity token이 주어졌을 때") {
            val rawNonce = "missing-kid-nonce"

            When("애플 로그인을 검증하면") {
                val exception =
                    shouldThrow<UnauthorizedException> {
                        client.authenticate(appleLogin(identityToken(claims(rawNonce), keyId = null), rawNonce))
                    }

                Then("AUTH-001 예외가 발생한다") {
                    exception.errorCode shouldBe AuthErrorCode.SOCIAL_AUTHENTICATION_FAILED
                }
            }
        }

        Given("JWT 형식이 아닌 identity token이 주어졌을 때") {
            When("애플 로그인을 검증하면") {
                val exception =
                    shouldThrow<UnauthorizedException> {
                        client.authenticate(appleLogin("not-a-jwt", "malformed-nonce"))
                    }

                Then("AUTH-001 예외가 발생한다") {
                    exception.errorCode shouldBe AuthErrorCode.SOCIAL_AUTHENTICATION_FAILED
                }
            }
        }

        Given("JWKS가 같은 kid를 RS256이 아닌 키로만 내려줄 때") {
            val coldClient = appleClient()
            stub.updateAndGet {
                it.copy(jwks = """{"keys":[{"kty":"EC","kid":"$PRIMARY_KEY_ID","alg":"ES256","n":"AQAB","e":"AQAB"}]}""")
            }
            val rawNonce = "filtered-jwk-nonce"

            When("애플 로그인을 검증하면") {
                val exception =
                    shouldThrow<UnauthorizedException> {
                        coldClient.authenticate(appleLogin(identityToken(claims(rawNonce)), rawNonce))
                    }

                Then("RSA/RS256이 아닌 키는 걸러내 AUTH-001 예외가 발생한다") {
                    exception.errorCode shouldBe AuthErrorCode.SOCIAL_AUTHENTICATION_FAILED
                }
            }
        }

        Given("JWKS에 없는 kid로 서명된 identity token이 주어졌을 때") {
            val rawNonce = "unknown-kid-nonce"

            When("애플 로그인을 검증하면") {
                val exception =
                    shouldThrow<UnauthorizedException> {
                        client.authenticate(appleLogin(identityToken(claims(rawNonce), keyId = "unknown-kid"), rawNonce))
                    }

                Then("AUTH-001 예외가 발생한다") {
                    exception.errorCode shouldBe AuthErrorCode.SOCIAL_AUTHENTICATION_FAILED
                }
            }
        }

        Given("이전 키를 캐시해 둔 클라이언트에 애플이 서명 키를 회전했을 때") {
            val rotatingClient = appleClient()
            val rawNonce = "rotation-nonce"
            rotatingClient.authenticate(appleLogin(identityToken(claims(rawNonce)), rawNonce))
            stub.updateAndGet { it.copy(jwks = rotatedJwks) }

            When("회전된 키로 서명된 identity token을 검증하면") {
                val profile =
                    rotatingClient.authenticate(
                        appleLogin(identityToken(claims(rawNonce), ROTATED_KEY_ID, rotated.second), rawNonce),
                    )

                Then("새 키로 검증에 성공한다") {
                    profile.providerUserId shouldBe "apple-user-id"
                }

                Then("캐시에 없는 kid라 JWKS를 다시 조회한다") {
                    jwksFetchCount.get() shouldBe 2
                }
            }
        }

        Given("JWKS 캐시 수명이 남아 있는 클라이언트가 있을 때") {
            val cachingClient = appleClient()
            val rawNonce = "cached-jwks-nonce"
            cachingClient.authenticate(appleLogin(identityToken(claims(rawNonce)), rawNonce))

            When("같은 kid로 다시 검증하면") {
                val profile = cachingClient.authenticate(appleLogin(identityToken(claims(rawNonce)), rawNonce))

                Then("검증에 성공한다") {
                    profile.providerUserId shouldBe "apple-user-id"
                }

                Then("캐시를 사용해 JWKS를 다시 조회하지 않는다") {
                    jwksFetchCount.get() shouldBe 1
                }
            }
        }

        Given("JWKS 캐시 수명이 0인 클라이언트가 있을 때") {
            val uncachedClient = appleClient(jwksCacheSeconds = 0)
            val rawNonce = "uncached-jwks-nonce"
            uncachedClient.authenticate(appleLogin(identityToken(claims(rawNonce)), rawNonce))

            When("같은 kid로 다시 검증하면") {
                val profile = uncachedClient.authenticate(appleLogin(identityToken(claims(rawNonce)), rawNonce))

                Then("검증에 성공한다") {
                    profile.providerUserId shouldBe "apple-user-id"
                }

                Then("매번 JWKS를 다시 조회한다") {
                    jwksFetchCount.get() shouldBe 2
                }
            }
        }

        Given("애플 JWKS 엔드포인트가 500을 반환할 때") {
            val coldClient = appleClient()
            stub.updateAndGet { it.copy(jwksStatus = 500, jwks = """{"error":"internal"}""") }
            val rawNonce = "jwks-failure-nonce"

            When("애플 로그인을 검증하면") {
                val exception =
                    shouldThrow<UnauthorizedException> {
                        coldClient.authenticate(appleLogin(identityToken(claims(rawNonce)), rawNonce))
                    }

                Then("AUTH-001 예외가 발생한다") {
                    exception.errorCode shouldBe AuthErrorCode.SOCIAL_AUTHENTICATION_FAILED
                }
            }
        }
    })

private fun rsaKeyPair(): Pair<RSAPublicKey, RSAPrivateKey> =
    KeyPairGenerator
        .getInstance("RSA")
        .apply { initialize(2048) }
        .generateKeyPair()
        .let { it.public as RSAPublicKey to it.private as RSAPrivateKey }

private fun jwksOf(vararg keys: Pair<String, RSAPublicKey>): String =
    keys.joinToString(",", """{"keys":[""", "]}") { (keyId, key) ->
        """{"kty":"RSA","kid":"$keyId","alg":"RS256","n":"${key.modulus.base64Url()}","e":"${key.publicExponent.base64Url()}"}"""
    }

private fun BigInteger.base64Url(): String {
    val bytes = toByteArray()
    val unsigned = if (bytes.size > 1 && bytes.first() == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
    return Base64
        .getUrlEncoder()
        .withoutPadding()
        .encodeToString(unsigned)
}
