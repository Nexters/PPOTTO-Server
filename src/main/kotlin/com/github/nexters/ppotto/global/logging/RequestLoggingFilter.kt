package com.github.nexters.ppotto.global.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.Collections
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestLoggingFilter : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ): Unit =
        UUID
            .randomUUID()
            .toString()
            .substring(0, 8)
            .also { MDC.put("requestId", it) }
            .let { System.currentTimeMillis() }
            .let { started ->
                try {
                    filterChain.doFilter(request, response)
                } finally {
                    log
                        .info(
                            "{} {} {} {}ms headers={}",
                            request.method,
                            request.requestURI,
                            response.status,
                            System.currentTimeMillis() - started,
                            request.maskedHeaders(),
                        ).let { MDC.clear() }
                }
            }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = request.requestURI.startsWith("/actuator")

    private fun HttpServletRequest.maskedHeaders(): String =
        Collections
            .list(headerNames)
            .joinToString(prefix = "[", postfix = "]") { name ->
                "$name=${maskedHeaderValue(name)}"
            }

    private fun HttpServletRequest.maskedHeaderValue(name: String): String =
        when {
            name.equals(AUTHORIZATION, ignoreCase = true) -> MASKED
            else ->
                Collections
                    .list(getHeaders(name))
                    .joinToString(",")
        }

    private companion object {
        const val AUTHORIZATION = "Authorization"
        const val MASKED = "***"
    }
}
