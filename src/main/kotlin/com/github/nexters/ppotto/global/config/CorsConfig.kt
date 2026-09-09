package com.github.nexters.ppotto.global.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class CorsConfig {
    @Bean
    fun corsConfigurationSource(corsProperties: CorsProperties): CorsConfigurationSource =
        CorsConfiguration()
            .apply {
                allowedOriginPatterns = corsProperties.allowedOrigins
                allowedMethods = listOf("*")
                allowedHeaders = listOf("*")
                maxAge = MAX_AGE_SECONDS
            }.let { configuration ->
                UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", configuration) }
            }

    private companion object {
        const val MAX_AGE_SECONDS = 3600L
    }
}
