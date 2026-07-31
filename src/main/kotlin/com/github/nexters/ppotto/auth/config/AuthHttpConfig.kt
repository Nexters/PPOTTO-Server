package com.github.nexters.ppotto.auth.config

import com.github.nexters.ppotto.auth.infrastructure.oauth.AppleOAuthHttpService
import com.github.nexters.ppotto.auth.infrastructure.oauth.KakaoOAuthHttpService
import org.springframework.context.annotation.Configuration
import org.springframework.web.service.registry.ImportHttpServices

@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = "kakao-oauth", types = [KakaoOAuthHttpService::class])
@ImportHttpServices(group = "apple-oauth", types = [AppleOAuthHttpService::class])
class AuthHttpConfig
