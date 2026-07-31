package com.github.nexters.ppotto.auth.infrastructure.oauth

import com.github.nexters.ppotto.auth.config.AppleAuthProperties
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.interfaces.ECPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Clock
import java.time.Duration
import java.util.Base64
import java.util.Date

@Component
class AppleClientSecretGenerator(
    private val properties: AppleAuthProperties,
) {
    private val clock = Clock.systemUTC()
    private val privateKey = loadPrivateKey(properties.privateKeyPath)

    fun generate(): String =
        clock
            .instant()
            .let { issuedAt ->
                JWTClaimsSet
                    .Builder()
                    .issuer(properties.teamId)
                    .subject(properties.clientId)
                    .audience(properties.issuer)
                    .issueTime(Date.from(issuedAt))
                    .expirationTime(Date.from(issuedAt.plus(Duration.ofDays(properties.clientSecretExpirationDays))))
                    .build()
            }.let { claims ->
                SignedJWT(
                    JWSHeader
                        .Builder(JWSAlgorithm.ES256)
                        .keyID(properties.keyId)
                        .build(),
                    claims,
                )
            }.also { it.sign(ECDSASigner(privateKey)) }
            .serialize()

    private fun loadPrivateKey(path: String): ECPrivateKey =
        Files
            .readString(Path.of(path))
            .replace(BEGIN_PRIVATE_KEY, "")
            .replace(END_PRIVATE_KEY, "")
            .replace("\\s".toRegex(), "")
            .let(Base64.getDecoder()::decode)
            .let(::PKCS8EncodedKeySpec)
            .let { KeyFactory.getInstance("EC").generatePrivate(it) as ECPrivateKey }

    private companion object {
        const val BEGIN_PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----"
        const val END_PRIVATE_KEY = "-----END PRIVATE KEY-----"
    }
}
