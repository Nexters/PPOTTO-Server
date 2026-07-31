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
    override fun shouldNotFilter(request: HttpServletRequest): Boolean = request.servletPath in PUBLIC_PATHS

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val authorization = request.getHeader(AUTHORIZATION)
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
    ) {
        try {
            val userId = tokenProvider.verifyAccessToken(token)
            val authentication = UsernamePasswordAuthenticationToken(userId, token, emptyList())
            authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
            val context = SecurityContextHolder.createEmptyContext()
            context.authentication = authentication
            SecurityContextHolder.setContext(context)
            filterChain.doFilter(request, response)
        } catch (e: UnauthorizedException) {
            SecurityContextHolder.clearContext()
            reject(request, response, e)
        }
    }

    private fun reject(
        request: HttpServletRequest,
        response: HttpServletResponse,
        cause: Exception? = null,
    ) {
        val exception =
            cause?.let { BadCredentialsException("Bearer token 인증에 실패했습니다.", it) }
                ?: BadCredentialsException("Bearer token 인증에 실패했습니다.")
        authenticationEntryPoint.commence(request, response, exception)
    }

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
    }
}
