package com.github.nexters.ppotto.auth.infrastructure.security

import com.github.nexters.ppotto.global.error.ErrorCode
import com.github.nexters.ppotto.global.error.ErrorResponse
import com.github.nexters.ppotto.global.response.ApiResponse
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets

@Component
class SecurityErrorWriter(
    private val objectMapper: ObjectMapper,
) {
    fun write(
        response: HttpServletResponse,
        errorCode: ErrorCode,
    ) {
        response.status = errorCode.status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = StandardCharsets.UTF_8.name()
        objectMapper.writeValue(response.outputStream, ApiResponse.error(ErrorResponse.of(errorCode)))
    }
}
