package com.github.nexters.ppotto.analysis.presentation

import com.github.nexters.ppotto.analysis.presentation.dto.CreateAnalysisRequest
import com.github.nexters.ppotto.analysis.presentation.dto.CreateAnalysisResponse
import com.github.nexters.ppotto.analysis.presentation.dto.StartUploadResponse
import com.github.nexters.ppotto.global.openapi.ConflictApiResponse
import com.github.nexters.ppotto.global.openapi.InvalidInputApiResponse
import com.github.nexters.ppotto.global.openapi.NotFoundApiResponse
import com.github.nexters.ppotto.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import java.util.UUID

@RequestMapping("/analysis", version = "1")
@Tag(name = "분석", description = "사진 업로드와 분석 실행")
interface AnalysisApi {
    @PostMapping
    @Operation(
        summary = "분석 생성",
        description = "보드를 지정하고 사진 90~100장의 업로드 URL을 한 번에 발급함",
    )
    @InvalidInputApiResponse
    @NotFoundApiResponse
    @ConflictApiResponse
    fun create(
        userId: UUID,
        request: CreateAnalysisRequest,
    ): ApiResponse<CreateAnalysisResponse>

    @PostMapping("/{analysisId}/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
        summary = "분석 시작",
        description = "업로드된 사진을 확인하고 분석 시작을 요청함",
    )
    @NotFoundApiResponse
    @ConflictApiResponse
    fun start(
        userId: UUID,
        analysisId: UUID,
    ): ApiResponse<StartUploadResponse>
}
