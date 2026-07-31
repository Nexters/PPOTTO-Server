package com.github.nexters.ppotto.auth.infrastructure.oauth

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.service.annotation.GetExchange
import java.net.URI

interface KakaoOAuthHttpService {
    @GetExchange
    fun getTokenInfo(
        uri: URI,
        @RequestHeader("Authorization") authorization: String,
    ): KakaoTokenInfo?

    @GetExchange
    fun getUserInfo(
        uri: URI,
        @RequestHeader("Authorization") authorization: String,
    ): KakaoUserInfo?
}

data class KakaoTokenInfo(
    val id: Long,
    @JsonProperty("app_id")
    val appId: Long,
)

data class KakaoUserInfo(
    val id: Long,
    @JsonProperty("kakao_account")
    val account: KakaoAccount?,
)

data class KakaoAccount(
    val email: String?,
)
