package com.github.nexters.ppotto.auth.infrastructure.oauth

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.service.annotation.GetExchange
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
}

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
