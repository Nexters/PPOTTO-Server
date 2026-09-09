package com.github.nexters.ppotto.auth.infrastructure.oauth

import com.github.nexters.ppotto.auth.config.AppleAuthProperties
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jwt.SignedJWT
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import java.net.URI
import java.time.Duration

private const val CLIENT_SECRET_EXPIRATION_DAYS = 180L

class AppleClientSecretGeneratorTest :
    BehaviorSpec({
        val properties =
            AppleAuthProperties(
                clientId = "com.nexters.ppotto",
                teamId = "TESTTEAM01",
                keyId = "TESTKEY001",
                privateKeyPath = "./src/test/resources/dummy-apple-key.p8",
                issuer = "https://appleid.apple.com",
                jwksUri = URI("https://appleid.apple.com/auth/keys"),
                tokenUri = URI("https://appleid.apple.com/auth/token"),
                revokeUri = URI("https://appleid.apple.com/auth/revoke"),
                clientSecretExpirationDays = CLIENT_SECRET_EXPIRATION_DAYS,
                jwksCacheSeconds = 3_600,
            )
        val generator = AppleClientSecretGenerator(properties)

        Given("애플 개발자 키와 팀 정보가 설정되었을 때") {
            When("client secret을 생성하면") {
                val jwt = SignedJWT.parse(generator.generate())
                val claims = jwt.jwtClaimsSet

                Then("ES256과 key id를 헤더에 담는다") {
                    jwt.header.algorithm shouldBe JWSAlgorithm.ES256
                    jwt.header.keyID shouldBe "TESTKEY001"
                }

                Then("iss는 팀 아이디, sub는 client id다") {
                    claims.issuer shouldBe "TESTTEAM01"
                    claims.subject shouldBe "com.nexters.ppotto"
                }

                Then("aud는 애플 issuer다") {
                    claims.audience shouldContainExactly listOf("https://appleid.apple.com")
                }

                Then("만료는 애플 상한인 180일을 넘지 않는다") {
                    val lifetime =
                        Duration.between(
                            claims.issueTime.toInstant(),
                            claims.expirationTime.toInstant(),
                        )
                    lifetime shouldBe Duration.ofDays(CLIENT_SECRET_EXPIRATION_DAYS)
                    lifetime shouldBeLessThanOrEqualTo Duration.ofDays(180)
                }
            }
        }
    })
