package com.github.nexters.ppotto.auth.config

import com.github.nexters.ppotto.auth.infrastructure.oauth.AppleOAuthApi
import com.github.nexters.ppotto.auth.infrastructure.oauth.KakaoOAuthApi
import org.springframework.context.annotation.Configuration
import org.springframework.web.service.registry.ImportHttpServices

@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = "oauth", types = [KakaoOAuthApi::class, AppleOAuthApi::class])
class AuthHttpConfig
