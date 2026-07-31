package com.github.nexters.ppotto.global.openapi

import io.swagger.v3.oas.annotations.responses.ApiResponse

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponse(responseCode = "400", description = "요청 값이 올바르지 않음")
annotation class InvalidInputApiResponse

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponse(responseCode = "404", description = "요청한 리소스를 찾을 수 없음")
annotation class NotFoundApiResponse

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponse(responseCode = "409", description = "현재 상태와 요청이 충돌함")
annotation class ConflictApiResponse
