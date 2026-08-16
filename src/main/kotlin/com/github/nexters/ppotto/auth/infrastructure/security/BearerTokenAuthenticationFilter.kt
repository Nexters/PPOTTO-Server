package com.github.nexters.ppotto.auth.infrastructure.security

import com.github.nexters.ppotto.auth.application.port.TokenProvider
import com.github.nexters.ppotto.auth.infrastructure.token.JwtAccessTokenVerificationException
import com.github.nexters.ppotto.global.config.PublicPaths
import com.github.nexters.ppotto.global.error.UnauthorizedException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
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
    private val environment: Environment,
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.servletPath.let { path ->
            PublicPaths.isPublicApi(path) || PublicPaths.isDocument(path)
        }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ): Unit =
        request.getHeader(AUTHORIZATION).let { authorization ->
            when {
                authorization == null -> {
                    logAuthFailure(request, MISSING_AUTHORIZATION, null)
                    filterChain.doFilter(request, response)
                }
                !authorization.startsWith(BEARER_PREFIX) -> {
                    logAuthFailure(request, INVALID_AUTHORIZATION_SCHEME, authorization)
                    reject(request, response)
                }
                authorization.removePrefix(BEARER_PREFIX).isBlank() -> {
                    logAuthFailure(request, BLANK_BEARER_TOKEN, authorization)
                    reject(request, response)
                }
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
                .let { UsernamePasswordAuthenticationToken(it.value, token, emptyList()) }
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
                .also { logTokenVerificationFailure(request, token, e) }
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

    private fun logTokenVerificationFailure(
        request: HttpServletRequest,
        token: String,
        exception: UnauthorizedException,
    ) {
        if (!isDev()) return
        when (exception) {
            is JwtAccessTokenVerificationException ->
                log.info(
                    "auth failed reason={} method={} uri={} authorization={} issuer={} subject={} tokenUse={}",
                    exception
                        .reason
                        .name
                        .lowercase(),
                    request.method,
                    request.requestURI,
                    "$BEARER_PREFIX$token",
                    exception.issuer,
                    exception.subject,
                    exception.tokenUse,
                )
            else ->
                log.info(
                    "auth failed reason={} method={} uri={} authorization={}",
                    JWT_VERIFICATION_FAILED,
                    request.method,
                    request.requestURI,
                    "$BEARER_PREFIX$token",
                )
        }
    }

    private fun logAuthFailure(
        request: HttpServletRequest,
        reason: String,
        authorization: String?,
    ) {
        if (isDev() && shouldLogImmediateFailure(request)) {
            log.info(
                "auth failed reason={} method={} uri={} authorization={}",
                reason,
                request.method,
                request.requestURI,
                authorization,
            )
        }
    }

    private fun shouldLogImmediateFailure(request: HttpServletRequest): Boolean = request.method != GET || request.servletPath != TERMS_PATH

    private fun isDev(): Boolean = environment.getProperty(DEPLOY_ENV) == DEV

    private companion object {
        const val AUTHORIZATION = "Authorization"
        const val BEARER_PREFIX = "Bearer "
        const val DEPLOY_ENV = "DEPLOY_ENV"
        const val DEV = "dev"
        const val GET = "GET"
        const val TERMS_PATH = "/terms"
        const val MISSING_AUTHORIZATION = "missing_authorization"
        const val INVALID_AUTHORIZATION_SCHEME = "invalid_authorization_scheme"
        const val BLANK_BEARER_TOKEN = "blank_bearer_token"
        const val JWT_VERIFICATION_FAILED = "jwt_verification_failed"
    }
}
