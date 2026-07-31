package com.github.nexters.ppotto.auth.infrastructure.oauth

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.PostExchange
import java.net.URI

internal interface AppleOAuthApi {
    @GetExchange
    fun jwks(uri: URI): AppleJwksResponse?

    @PostExchange(contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    fun exchangeToken(
        uri: URI,
        @RequestParam("client_id") clientId: String,
        @RequestParam("client_secret") clientSecret: String,
        @RequestParam("code") code: String,
        @RequestParam("grant_type") grantType: String,
    ): AppleTokenResponse?

    @PostExchange(contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    fun revoke(
        uri: URI,
        @RequestParam("client_id") clientId: String,
        @RequestParam("client_secret") clientSecret: String,
        @RequestParam("token") token: String,
        @RequestParam("token_type_hint") tokenTypeHint: String,
    )
}

internal data class AppleTokenResponse(
    @JsonProperty("refresh_token")
    val refreshToken: String?,
)

internal data class AppleJwksResponse(
    val keys: List<AppleJwk>,
)

internal data class AppleJwk(
    @JsonProperty("kty")
    val keyType: String,
    @JsonProperty("kid")
    val keyId: String,
    @JsonProperty("alg")
    val algorithm: String,
    @JsonProperty("n")
    val modulus: String,
    @JsonProperty("e")
    val exponent: String,
)
