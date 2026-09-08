package com.github.nexters.ppotto.auth.config

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.net.URI

@Validated
@ConfigurationProperties("auth.kakao")
data class KakaoAuthProperties(
    @field:Positive
    val appId: Long,

    @field:NotNull
    val accessTokenInfoUri: URI,

    @field:NotNull
    val userInfoUri: URI,

    @field:NotBlank
    val clientId: String,

    @field:NotBlank
    val clientSecret: String,

    @field:NotNull
    val tokenUri: URI,
)
