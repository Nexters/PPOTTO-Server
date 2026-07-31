package com.github.nexters.ppotto.analysis.presentation

import com.github.nexters.ppotto.analysis.application.AnalysisService
import com.github.nexters.ppotto.analysis.presentation.dto.CreateAnalysisRequest
import com.github.nexters.ppotto.analysis.presentation.dto.CreateAnalysisResponse
import com.github.nexters.ppotto.analysis.presentation.dto.StartUploadResponse
import com.github.nexters.ppotto.global.response.ApiResponse
import com.github.nexters.ppotto.global.security.AuthenticatedUser
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class AnalysisController(
    private val analysisService: AnalysisService,
) : AnalysisApi {
    override fun create(
        @AuthenticatedUser userId: UUID,
        @Valid @RequestBody request: CreateAnalysisRequest,
    ): ApiResponse<CreateAnalysisResponse> {
        val result = analysisService.createAnalysis(userId, request.boardId, request.photos.map { it.toServiceRequest() })
        return ApiResponse.success(CreateAnalysisResponse.from(result))
    }

    override fun start(
        @AuthenticatedUser userId: UUID,
        @PathVariable analysisId: UUID,
    ): ApiResponse<StartUploadResponse> {
        val result = analysisService.startUpload(userId, analysisId)
        return ApiResponse.success(StartUploadResponse.from(result))
    }
}
