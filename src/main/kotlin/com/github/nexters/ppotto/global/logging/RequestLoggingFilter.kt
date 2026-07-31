package com.github.nexters.ppotto.global.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
class RequestLoggingFilter : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        MDC.put(
            "requestId",
            UUID
                .randomUUID()
                .toString()
                .substring(0, 8),
        )
        System.currentTimeMillis().let { started ->
            try {
                filterChain.doFilter(request, response)
            } finally {
                log.info(
                    "{} {} {} {}ms",
                    request.method,
                    request.requestURI,
                    response.status,
                    System.currentTimeMillis() - started,
                )
                MDC.clear()
            }
        }
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = request.requestURI.startsWith("/actuator")
}
