package com.github.nexters.ppotto.global.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer

/**
 * `@ImportHttpServices`로 등록되는 모든 그룹(pixian, oauth 등)의 RestClient가 JDK 내장
 * HttpClient만 쓰도록 고정한다. 클래스패스에 Apache HttpClient5 등이 새로 추가되면 Spring이
 * 자동으로 그쪽을 골라 쓰는데, 그런 라이브러리는 5xx 등에 자체적으로 재시도를 하는 경우가 있어
 * 호출부의 명시적 재시도 로직과 겹쳐 실제 요청 횟수가 의도보다 늘어날 수 있다. 어떤 의존성이
 * 추가되든 외부 API 호출 동작이 바뀌지 않도록 하나로 고정한다.
 */
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
