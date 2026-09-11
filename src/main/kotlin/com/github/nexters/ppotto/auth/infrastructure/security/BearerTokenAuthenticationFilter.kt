package com.github.nexters.ppotto.auth.infrastructure.security

import com.github.nexters.ppotto.auth.application.port.TokenProvider
import com.github.nexters.ppotto.global.error.UnauthorizedException
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.web.PublicPaths
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
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.servletPath
        return PublicPaths.isPublicApi(path) || PublicPaths.isDocument(path)
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val authorization = request.getHeader(AUTHORIZATION)
        if (authorization == null) {
            filterChain.doFilter(request, response)
            return
        }
        val token = authorization.removePrefix(BEARER_PREFIX).trim()
        val userId =
            if (authorization.startsWith(BEARER_PREFIX) && token.isNotEmpty()) verifyOrNull(token) else null
        if (userId == null) {
            SecurityContextHolder.clearContext()
            reject(request, response)
            return
        }

        authenticate(userId, token, request)
        filterChain.doFilter(request, response)
    }

    private fun verifyOrNull(token: String): UserId? =
        try {
            tokenProvider.verifyAccessToken(token)
        } catch (_: UnauthorizedException) {
            null
        }

    private fun authenticate(
        userId: UserId,
        token: String,
        request: HttpServletRequest,
    ) {
        val authentication =
            UsernamePasswordAuthenticationToken(userId.value, token, emptyList()).apply {
                details = WebAuthenticationDetailsSource().buildDetails(request)
            }
        SecurityContextHolder.setContext(
            SecurityContextHolder.createEmptyContext().apply { this.authentication = authentication },
        )
    }

    private fun reject(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) = authenticationEntryPoint.commence(request, response, BadCredentialsException(AUTHENTICATION_FAILED_MESSAGE))

    private companion object {
        const val AUTHORIZATION = "Authorization"
        const val BEARER_PREFIX = "Bearer "
        const val AUTHENTICATION_FAILED_MESSAGE = "Bearer token 인증에 실패했습니다."
    }
}
