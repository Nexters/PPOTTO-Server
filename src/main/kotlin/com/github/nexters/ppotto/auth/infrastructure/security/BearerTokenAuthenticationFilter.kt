package com.github.nexters.ppotto.auth.infrastructure.security

import com.github.nexters.ppotto.auth.application.port.TokenProvider
import com.github.nexters.ppotto.global.error.UnauthorizedException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class BearerTokenAuthenticationFilter(
    private val tokenProvider: TokenProvider,
    private val authenticationEntryPoint: AuthAuthenticationEntryPoint,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.servletPath.let { path ->
            path in PUBLIC_PATHS || DOCUMENT_PATH_PREFIXES.any(path::startsWith)
        }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ): Unit =
        request.getHeader(AUTHORIZATION).let { authorization ->
            when {
                authorization == null -> filterChain.doFilter(request, response)
                !authorization.startsWith(BEARER_PREFIX) -> reject(request, response)
                authorization.removePrefix(BEARER_PREFIX).isBlank() -> reject(request, response)
                else -> authenticate(authorization.removePrefix(BEARER_PREFIX).trim(), request, response, filterChain)
            }
        }

    private fun authenticate(
        token: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ): Unit =
        try {
            tokenProvider
                .verifyAccessToken(token)
                .let { UsernamePasswordAuthenticationToken(it, token, emptyList()) }
                .apply { details = WebAuthenticationDetailsSource().buildDetails(request) }
                .let { authentication ->
                    SecurityContextHolder
                        .createEmptyContext()
                        .apply { this.authentication = authentication }
                }.also(SecurityContextHolder::setContext)
                .let { filterChain.doFilter(request, response) }
        } catch (e: UnauthorizedException) {
            SecurityContextHolder
                .clearContext()
                .let { reject(request, response, e) }
        }

    private fun reject(
        request: HttpServletRequest,
        response: HttpServletResponse,
        cause: Exception? = null,
    ) = (
        cause?.let { BadCredentialsException("Bearer token 인증에 실패했습니다.", it) }
            ?: BadCredentialsException("Bearer token 인증에 실패했습니다.")
    ).let { authenticationEntryPoint.commence(request, response, it) }

    private companion object {
        const val AUTHORIZATION = "Authorization"
        const val BEARER_PREFIX = "Bearer "
        val PUBLIC_PATHS =
            setOf(
                "/auth/login",
                "/auth/refresh",
                "/actuator/health",
                "/actuator/health/liveness",
                "/actuator/health/readiness",
            )
        val DOCUMENT_PATH_PREFIXES =
            setOf(
                "/swagger-ui",
                "/v3/api-docs",
            )
    }
}
