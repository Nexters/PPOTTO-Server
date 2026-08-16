package com.github.nexters.ppotto.global.observability

import com.github.nexters.ppotto.global.config.PublicPaths
import io.sentry.ISpan
import io.sentry.Sentry
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.ContentCachingResponseWrapper
import java.util.Collections

@Component
@Order(SENTRY_HTTP_PAYLOAD_FILTER_ORDER)
class SentryHttpPayloadFilter : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.servletPath.let { path ->
            PublicPaths.isDocument(path) || path.startsWith(ACTUATOR_PATH_PREFIX)
        }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val span = Sentry.getSpan()
        if (span == null || span.isNoOp) {
            filterChain.doFilter(request, response)
            return
        }

        val cachedRequest = request as? ContentCachingRequestWrapper ?: ContentCachingRequestWrapper(request, CACHE_LIMIT)
        val cachedResponse = ContentCachingResponseWrapper(response)
        try {
            filterChain.doFilter(cachedRequest, cachedResponse)
        } finally {
            if (!request.isAsyncStarted) {
                span.record(cachedRequest, cachedResponse)
                cachedResponse.copyBodyToResponse()
            }
        }
    }

    private fun ISpan.record(
        request: ContentCachingRequestWrapper,
        response: ContentCachingResponseWrapper,
    ) {
        applyAll { HttpPayloadAttributes.requestBody(request.cachedBody(), request.contentType, request.characterEncoding) }
        applyAll { HttpPayloadAttributes.responseBody(response.contentAsByteArray, response.contentType, response.characterEncoding) }
        applyAll { HttpPayloadAttributes.requestHeaders(request.headerMap()) }
        applyAll { HttpPayloadAttributes.responseHeaders(response.headerMap()) }
    }

    private fun ISpan.applyAll(attributes: () -> Map<String, Any>) {
        runCatching(attributes)
            .getOrDefault(emptyMap())
            .forEach(::setData)
    }

    private fun ContentCachingRequestWrapper.headerMap(): Map<String, List<String>> =
        Collections
            .list(headerNames)
            .associateWith { name -> Collections.list(getHeaders(name)) }

    private fun ContentCachingResponseWrapper.headerMap(): Map<String, List<String>> =
        headerNames.associateWith { name -> getHeaders(name).toList() }

    private fun ContentCachingRequestWrapper.cachedBody(): ByteArray =
        contentAsByteArray.takeIf { it.isNotEmpty() }
            ?: runCatching {
                if (contentLength in 1..CACHE_LIMIT) inputStream.readAllBytes()
                contentAsByteArray
            }.getOrDefault(EMPTY_BODY)

    private companion object {
        const val ACTUATOR_PATH_PREFIX = "/actuator"
        const val CACHE_LIMIT = HttpPayloadAttributes.REQUEST_CACHE_LIMIT_BYTES
        val EMPTY_BODY = ByteArray(0)
    }
}

const val SENTRY_HTTP_PAYLOAD_FILTER_ORDER: Int = Ordered.HIGHEST_PRECEDENCE + 2
