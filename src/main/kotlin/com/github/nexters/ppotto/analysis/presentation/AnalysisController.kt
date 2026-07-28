package com.github.nexters.ppotto.analysis.presentation

import com.github.nexters.ppotto.analysis.application.AnalysisService
import com.github.nexters.ppotto.analysis.presentation.dto.CreateAnalysisRequest
import com.github.nexters.ppotto.analysis.presentation.dto.CreateAnalysisResponse
import com.github.nexters.ppotto.analysis.presentation.dto.StartUploadResponse
import com.github.nexters.ppotto.global.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/analysis", version = "1")
class AnalysisController(
    private val analysisService: AnalysisService,
) {
    @PostMapping
    fun create(
        @Valid @RequestBody request: CreateAnalysisRequest,
    ): ApiResponse<CreateAnalysisResponse> {
        val result = analysisService.createAnalysis(request.boardId, request.photos.map { it.toServiceRequest() })
        return ApiResponse.success(CreateAnalysisResponse.from(result))
    }

    @PostMapping("/{analysisId}/start")
    fun start(
        @PathVariable analysisId: UUID,
    ): ApiResponse<StartUploadResponse> {
        val result = analysisService.startUpload(analysisId)
        return ApiResponse.success(StartUploadResponse.from(result))
    }
}
