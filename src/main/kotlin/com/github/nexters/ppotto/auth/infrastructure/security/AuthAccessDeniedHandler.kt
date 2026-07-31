package com.github.nexters.ppotto.auth.infrastructure.security

import com.github.nexters.ppotto.global.error.CommonErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component

@Component
class AuthAccessDeniedHandler(
    private val errorWriter: SecurityErrorWriter,
) : AccessDeniedHandler {
    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ): Unit = errorWriter.write(response, CommonErrorCode.FORBIDDEN)
}
