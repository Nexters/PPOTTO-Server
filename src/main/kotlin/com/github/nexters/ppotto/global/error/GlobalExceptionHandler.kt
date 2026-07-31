package com.github.nexters.ppotto.global.error

import com.github.nexters.ppotto.global.response.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(e: BusinessException): ResponseEntity<ApiResponse<Unit>> =
        respond(e.errorCode.status, ErrorResponse.of(e.errorCode, e.message), e)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Unit>> =
        respond(HttpStatus.BAD_REQUEST, ErrorResponse.of(CommonErrorCode.INVALID_INPUT, e.bindingResult), e)

    @ExceptionHandler(
        HandlerMethodValidationException::class,
        HttpMessageNotReadableException::class,
        MethodArgumentTypeMismatchException::class,
        MissingServletRequestParameterException::class,
    )
    fun handleBadRequest(e: Exception): ResponseEntity<ApiResponse<Unit>> =
        respond(HttpStatus.BAD_REQUEST, ErrorResponse.of(CommonErrorCode.INVALID_INPUT), e)

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFound(e: NoResourceFoundException): ResponseEntity<ApiResponse<Unit>> =
        respond(HttpStatus.NOT_FOUND, ErrorResponse.of(CommonErrorCode.RESOURCE_NOT_FOUND), e)

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(e: HttpRequestMethodNotSupportedException): ResponseEntity<ApiResponse<Unit>> =
        respond(HttpStatus.METHOD_NOT_ALLOWED, ErrorResponse.of(CommonErrorCode.METHOD_NOT_ALLOWED), e)

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ApiResponse<Unit>> =
        respond(HttpStatus.INTERNAL_SERVER_ERROR, ErrorResponse.of(CommonErrorCode.INTERNAL_ERROR), e)

    private fun respond(
        status: HttpStatus,
        error: ErrorResponse,
        e: Exception,
    ): ResponseEntity<ApiResponse<Unit>> =
        e
            .also {
                status
                    .takeIf(HttpStatus::is5xxServerError)
                    ?.let { log.error("unhandled exception", e) }
                    ?: log.warn("{}: {}", e::class.simpleName, e.message)
            }.let { ResponseEntity.status(status).body(ApiResponse.error(error)) }
}
