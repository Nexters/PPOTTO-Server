package com.github.nexters.ppotto.global.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    @Value("\${cors.allowed-origins}") private val allowedOrigins: List<String>,
) : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOrigins(*allowedOrigins.toTypedArray())
            .allowedMethods("*")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600)
    }
}
