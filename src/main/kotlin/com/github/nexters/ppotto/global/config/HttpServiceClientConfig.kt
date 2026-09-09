package com.github.nexters.ppotto.global.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer

@Configuration(proxyBeanMethods = false)
class HttpServiceClientConfig {
    @Bean
    fun jdkHttpClientGroupConfigurer(): RestClientHttpServiceGroupConfigurer =
        RestClientHttpServiceGroupConfigurer { groups ->
            groups.forEachClient { _, builder ->
                builder.requestFactory(JdkClientHttpRequestFactory())
            }
        }
}
