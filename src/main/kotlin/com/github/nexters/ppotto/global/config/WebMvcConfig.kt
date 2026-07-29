package com.github.nexters.ppotto.global.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfig : WebMvcConfigurer {
    override fun configureApiVersioning(configurer: ApiVersionConfigurer) {
        configurer
            .useRequestHeader("X-API-Version")
            .setDefaultVersion("1")
    }
}
