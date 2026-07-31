package com.github.nexters.ppotto.terms.support

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@TestConfiguration
class TermsTestSecurityConfig {
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    fun termsTestAuthenticationFilter() =
        object : OncePerRequestFilter() {
            override fun doFilterInternal(
                request: HttpServletRequest,
                response: HttpServletResponse,
                filterChain: FilterChain,
            ) {
                val userId = request.getAttribute(USER_ID_ATTRIBUTE) as UUID?
                if (userId != null) {
                    SecurityContextHolder.getContext().authentication =
                        UsernamePasswordAuthenticationToken(userId, null, emptyList())
                }
                filterChain.doFilter(request, response)
            }
        }

    companion object {
        const val USER_ID_ATTRIBUTE = "termsTestUserId"
    }
}
