package com.github.nexters.ppotto.analysis.presentation

import com.github.nexters.ppotto.analysis.application.AnalysisService
import com.github.nexters.ppotto.analysis.presentation.dto.CreateAnalysisRequest
import com.github.nexters.ppotto.analysis.presentation.dto.CreateAnalysisResponse
import com.github.nexters.ppotto.analysis.presentation.dto.StartUploadResponse
import com.github.nexters.ppotto.global.error.UnauthorizedException
import com.github.nexters.ppotto.global.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/analysis", version = "1")
class AnalysisController(
    private val analysisService: AnalysisService,
) {
    @PostMapping
    fun create(
        @AuthenticationPrincipal userId: UUID?,
        @Valid @RequestBody request: CreateAnalysisRequest,
    ): ApiResponse<CreateAnalysisResponse> {
        val result = analysisService.createAnalysis(userId.orThrow(), request.boardId, request.photos.map { it.toServiceRequest() })
        return ApiResponse.success(CreateAnalysisResponse.from(result))
    }

    @PostMapping("/{analysisId}/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun start(
        @AuthenticationPrincipal userId: UUID?,
        @PathVariable analysisId: UUID,
    ): ApiResponse<StartUploadResponse> {
        val result = analysisService.startUpload(userId.orThrow(), analysisId)
        return ApiResponse.success(StartUploadResponse.from(result))
    }

    private fun UUID?.orThrow(): UUID = this ?: throw UnauthorizedException()
}
