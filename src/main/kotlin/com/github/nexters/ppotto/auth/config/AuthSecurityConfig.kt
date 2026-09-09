package com.github.nexters.ppotto.auth.config

import com.github.nexters.ppotto.auth.infrastructure.security.AuthAuthenticationEntryPoint
import com.github.nexters.ppotto.auth.infrastructure.security.BearerTokenAuthenticationFilter
import com.github.nexters.ppotto.global.config.PublicPaths
import org.springframework.boot.web.servlet.FilterRegistrationBean
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

@Configuration(proxyBeanMethods = false)
class AuthSecurityConfig {
    @Bean
    @Order(SWAGGER_CHAIN_ORDER)
    @Profile("prod")
    fun swaggerSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .securityMatcher(*PublicPaths.DOCUMENT_PATTERNS)
            .csrf { it.disable() }
            .cors(Customizer.withDefaults())
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .httpBasic(Customizer.withDefaults())
            .build()

    @Bean
    @Order(API_CHAIN_ORDER)
    @Profile("!test | secured")
    fun apiSecurityFilterChain(
        http: HttpSecurity,
        bearerTokenAuthenticationFilter: BearerTokenAuthenticationFilter,
        authenticationEntryPoint: AuthAuthenticationEntryPoint,
    ): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .cors(Customizer.withDefaults())
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling { it.authenticationEntryPoint(authenticationEntryPoint) }
            .authorizeHttpRequests {
                it
                    .requestMatchers(*PublicPaths.PUBLIC_API_PATTERNS, *PublicPaths.DOCUMENT_PATTERNS)
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, *PublicPaths.OPTIONAL_AUTH_GET_PATTERNS)
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }.addFilterBefore(bearerTokenAuthenticationFilter, AnonymousAuthenticationFilter::class.java)
            .build()

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    @Profile("test & !secured")
    fun defaultSecurityFilterChain(
        http: HttpSecurity,
        bearerTokenAuthenticationFilter: BearerTokenAuthenticationFilter,
    ): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .cors(Customizer.withDefaults())
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .addFilterBefore(bearerTokenAuthenticationFilter, AnonymousAuthenticationFilter::class.java)
            .build()

    @Bean
    fun bearerTokenAuthenticationFilterRegistration(
        bearerTokenAuthenticationFilter: BearerTokenAuthenticationFilter,
    ): FilterRegistrationBean<BearerTokenAuthenticationFilter> =
        FilterRegistrationBean(bearerTokenAuthenticationFilter).apply { isEnabled = false }

    private companion object {
        const val SWAGGER_CHAIN_ORDER = 0
        const val API_CHAIN_ORDER = 100
    }
}
