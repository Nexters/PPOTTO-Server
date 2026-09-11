package com.github.nexters.ppotto.global.logging

import com.github.nexters.ppotto.global.observability.HttpPayloadAttributes
import com.github.nexters.ppotto.global.web.PublicPaths
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
    ) {
        val requestId =
            UUID
                .randomUUID()
                .toString()
                .substring(0, REQUEST_ID_LENGTH)
        val startedAt = System.currentTimeMillis()
        MDC.put(REQUEST_ID_KEY, requestId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            log.info(
                "{} {} {} {}ms headers={}",
                request.method,
                request.requestURI,
                response.status,
                System.currentTimeMillis() - startedAt,
                request.maskedHeaders(),
            )
            MDC.clear()
        }
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = PublicPaths.isActuator(request.requestURI)

    private fun HttpServletRequest.maskedHeaders(): String =
        Collections
            .list(headerNames)
            .joinToString(prefix = "[", postfix = "]") { name ->
                "$name=${maskedHeaderValue(name)}"
            }

    private fun HttpServletRequest.maskedHeaderValue(name: String): String {
        if (HttpPayloadAttributes.isSensitiveHeader(name)) return MASKED
        return Collections
            .list(getHeaders(name))
            .joinToString(HEADER_VALUE_SEPARATOR)
    }

    private companion object {
        const val REQUEST_ID_KEY = "requestId"
        const val REQUEST_ID_LENGTH = 8
        const val HEADER_VALUE_SEPARATOR = ","
        const val MASKED = "***"
    }
}
