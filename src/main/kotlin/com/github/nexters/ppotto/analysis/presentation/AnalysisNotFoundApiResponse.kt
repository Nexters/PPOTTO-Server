package com.github.nexters.ppotto.analysis.presentation

import com.github.nexters.ppotto.analysis.presentation.dto.AnalysisApiExamples
import com.github.nexters.ppotto.global.openapi.ApiErrorResponse
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponse(
    responseCode = "404",
    description = "분석을 찾을 수 없음 (ANALYSIS-005)",
    content = [
        Content(
            mediaType = "application/json",
            schema = Schema(implementation = ApiErrorResponse::class),
            examples = [
                ExampleObject(
                    name = "ANALYSIS-005",
                    summary = "분석 없음 또는 소유자 불일치",
                    value = AnalysisApiExamples.ANALYSIS_NOT_FOUND,
                ),
            ],
        ),
    ],
)
annotation class AnalysisNotFoundApiResponse
