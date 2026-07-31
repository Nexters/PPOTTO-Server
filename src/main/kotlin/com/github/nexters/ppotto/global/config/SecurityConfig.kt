package com.github.nexters.ppotto.global.config

import com.github.nexters.ppotto.auth.infrastructure.security.AuthAccessDeniedHandler
import com.github.nexters.ppotto.auth.infrastructure.security.AuthAuthenticationEntryPoint
import com.github.nexters.ppotto.auth.infrastructure.security.BearerTokenAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class SecurityConfig {
    companion object {
        const val SWAGGER_CHAIN_ORDER = 0
        const val API_CHAIN_ORDER = 100
        val SWAGGER_PATHS = arrayOf("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
        val PUBLIC_API_PATHS = arrayOf("/auth/login", "/auth/refresh", "/actuator/health/**")
    }

    @Bean
    @Order(SWAGGER_CHAIN_ORDER)
    @Profile("prod")
    fun swaggerSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .securityMatcher(*SWAGGER_PATHS)
            .csrf { it.disable() }
            .cors(Customizer.withDefaults())
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .httpBasic(Customizer.withDefaults())
            .build()

    @Bean
    @Order(API_CHAIN_ORDER)
    @Profile("!test")
    fun apiSecurityFilterChain(
        http: HttpSecurity,
        bearerTokenAuthenticationFilter: BearerTokenAuthenticationFilter,
        authenticationEntryPoint: AuthAuthenticationEntryPoint,
        accessDeniedHandler: AuthAccessDeniedHandler,
    ): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .cors(Customizer.withDefaults())
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling {
                it.authenticationEntryPoint(authenticationEntryPoint)
                it.accessDeniedHandler(accessDeniedHandler)
            }.authorizeHttpRequests {
                it
                    .requestMatchers(*PUBLIC_API_PATHS, *SWAGGER_PATHS)
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/terms")
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }.addFilterBefore(bearerTokenAuthenticationFilter, AnonymousAuthenticationFilter::class.java)
            .build()

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    fun defaultSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .cors(Customizer.withDefaults())
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()

    @Bean
    fun corsConfigurationSource(corsProperties: CorsProperties): CorsConfigurationSource =
        CorsConfiguration()
            .apply {
                allowedOrigins = corsProperties.allowedOrigins
                allowedMethods = listOf("*")
                allowedHeaders = listOf("*")
                allowCredentials = true
                maxAge = 3600
            }.let { configuration ->
                UrlBasedCorsConfigurationSource().apply {
                    registerCorsConfiguration("/**", configuration)
                }
            }
}
