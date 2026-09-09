package com.github.nexters.ppotto.auth.presentation

import com.github.nexters.ppotto.global.openapi.ApiErrorResponse
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponse(
    responseCode = "400",
    description = "요청 값이 올바르지 않거나 가입에 필요한 값이 없음 (COMMON-001, AUTH-006, AUTH-007)",
    content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
)
annotation class SignupInvalidInputApiResponse

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponse(
    responseCode = "401",
    description = "소셜 로그인 검증 또는 애플 authorization code 교환에 실패함 (AUTH-001, AUTH-003)",
    content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
)
annotation class LoginUnauthorizedApiResponse

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponse(
    responseCode = "401",
    description = "카카오 authorization code 교환 또는 소셜 로그인 검증에 실패함 (AUTH-001, AUTH-008)",
    content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
)
annotation class WebLoginUnauthorizedApiResponse

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponse(
    responseCode = "401",
    description = "refresh token이 유효하지 않음 (AUTH-002)",
    content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
)
annotation class InvalidRefreshTokenApiResponse

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponse(
    responseCode = "403",
    description = "가입에 필요한 동의가 부족함 (AUTH-004, AUTH-005)",
    content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
)
annotation class ConsentRequiredApiResponse
