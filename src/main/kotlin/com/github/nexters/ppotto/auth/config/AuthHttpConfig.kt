package com.github.nexters.ppotto.auth.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
class AuthHttpConfig {
    @Bean
    @ConditionalOnMissingBean
    fun restClientBuilder(properties: OAuthHttpProperties): RestClient.Builder {
        val requestFactory =
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMillis))
                setReadTimeout(Duration.ofMillis(properties.readTimeoutMillis))
            }
        return RestClient.builder().requestFactory(requestFactory)
    }
}
