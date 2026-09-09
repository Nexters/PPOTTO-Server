package com.github.nexters.ppotto.analysis.presentation

import com.github.nexters.ppotto.global.openapi.ApiErrorResponse
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponse(
    responseCode = "404",
    description = "분석을 찾을 수 없음 (ANALYSIS-005)",
    content = [Content(mediaType = MEDIA_TYPE_JSON, schema = Schema(implementation = ApiErrorResponse::class))],
)
annotation class AnalysisNotFoundApiResponse

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponse(
    responseCode = "400",
    description = "요청 값이 올바르지 않음 (COMMON-001, ANALYSIS-001, ANALYSIS-009)",
    content = [Content(mediaType = MEDIA_TYPE_JSON, schema = Schema(implementation = ApiErrorResponse::class))],
)
annotation class CreateAnalysisInvalidInputApiResponse

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponse(
    responseCode = "404",
    description = "보드를 찾을 수 없음 (BOARD-002)",
    content = [Content(mediaType = MEDIA_TYPE_JSON, schema = Schema(implementation = ApiErrorResponse::class))],
)
annotation class AnalysisBoardNotFoundApiResponse

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponse(
    responseCode = "409",
    description = "진행 중인 분석이 이미 있음 (ANALYSIS-002)",
    content = [Content(mediaType = MEDIA_TYPE_JSON, schema = Schema(implementation = ApiErrorResponse::class))],
)
annotation class ActiveAnalysisExistsApiResponse

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponse(
    responseCode = "409",
    description = "이미 시작되었거나 종료된 분석임 (ANALYSIS-003)",
    content = [Content(mediaType = MEDIA_TYPE_JSON, schema = Schema(implementation = ApiErrorResponse::class))],
)
annotation class AnalysisAlreadyStartedApiResponse

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponse(
    responseCode = "409",
    description = "현재 상태와 요청이 충돌함 (ANALYSIS-003, ANALYSIS-008)",
    content = [Content(mediaType = MEDIA_TYPE_JSON, schema = Schema(implementation = ApiErrorResponse::class))],
)
annotation class AnalysisStartConflictApiResponse

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponse(
    responseCode = "409",
    description = "취소할 수 없는 상태의 분석임 (ANALYSIS-004)",
    content = [Content(mediaType = MEDIA_TYPE_JSON, schema = Schema(implementation = ApiErrorResponse::class))],
)
annotation class AnalysisCancelNotAllowedApiResponse

private const val MEDIA_TYPE_JSON = "application/json"
