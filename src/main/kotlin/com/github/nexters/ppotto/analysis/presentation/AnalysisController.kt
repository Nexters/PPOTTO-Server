package com.github.nexters.ppotto.analysis.presentation

import com.github.nexters.ppotto.analysis.application.AnalysisService
import com.github.nexters.ppotto.analysis.presentation.dto.CreateAnalysisRequest
import com.github.nexters.ppotto.analysis.presentation.dto.CreateAnalysisResponse
import com.github.nexters.ppotto.analysis.presentation.dto.StartUploadResponse
import com.github.nexters.ppotto.global.openapi.ConflictApiResponse
import com.github.nexters.ppotto.global.openapi.InvalidInputApiResponse
import com.github.nexters.ppotto.global.openapi.NotFoundApiResponse
import com.github.nexters.ppotto.global.response.ApiResponse
import com.github.nexters.ppotto.global.security.AuthenticatedUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/analysis", version = "1")
@Tag(name = "분석", description = "사진 업로드와 분석 실행")
class AnalysisController(
    private val analysisService: AnalysisService,
) {
    @PostMapping
    @Operation(
        summary = "분석 생성",
        description = "보드를 지정하고 사진 90~100장의 업로드 URL을 한 번에 발급함",
    )
    @InvalidInputApiResponse
    @NotFoundApiResponse
    @ConflictApiResponse
    fun create(
        @AuthenticatedUser userId: UUID,
        @Valid @RequestBody request: CreateAnalysisRequest,
    ): ApiResponse<CreateAnalysisResponse> {
        val result = analysisService.createAnalysis(userId, request.boardId, request.photos.map { it.toServiceRequest() })
        return ApiResponse.success(CreateAnalysisResponse.from(result))
    }

    @PostMapping("/{analysisId}/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
        summary = "분석 시작",
        description = "업로드된 사진을 확인하고 분석 시작을 요청함",
    )
    @NotFoundApiResponse
    @ConflictApiResponse
    fun start(
        @AuthenticatedUser userId: UUID,
        @PathVariable analysisId: UUID,
    ): ApiResponse<StartUploadResponse> {
        val result = analysisService.startUpload(userId, analysisId)
        return ApiResponse.success(StartUploadResponse.from(result))
    }
}
