package com.github.nexters.ppotto.auth.infrastructure.oauth

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.PostExchange
import java.net.URI

internal interface KakaoOAuthApi {
    @GetExchange
    fun tokenInfo(
        uri: URI,
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String,
    ): KakaoTokenInfo?

    @GetExchange
    fun userInfo(
        uri: URI,
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String,
    ): KakaoUserInfo?

    @PostExchange(contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    fun exchangeToken(
        uri: URI,
        @RequestParam("grant_type") grantType: String,
        @RequestParam("client_id") clientId: String,
        @RequestParam("client_secret") clientSecret: String,
        @RequestParam("redirect_uri") redirectUri: String,
        @RequestParam("code") code: String,
    ): KakaoTokenResponse?
}

internal data class KakaoTokenResponse(
    @JsonProperty("access_token")
    val accessToken: String?,
)

internal data class KakaoTokenInfo(
    val id: Long,

    @JsonProperty("app_id")
    val appId: Long,
)

internal data class KakaoUserInfo(
    val id: Long,

    @JsonProperty("kakao_account")
    val account: KakaoAccount?,
)

internal data class KakaoAccount(
    val email: String?,
    val profile: KakaoProfile?,
)

internal data class KakaoProfile(
    val nickname: String?,
)
