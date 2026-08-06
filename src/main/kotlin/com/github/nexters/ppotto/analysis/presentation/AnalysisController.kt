package com.github.nexters.ppotto.analysis.presentation

import com.github.nexters.ppotto.analysis.application.AnalysisQueryService
import com.github.nexters.ppotto.analysis.application.AnalysisService
import com.github.nexters.ppotto.analysis.presentation.dto.AnalysisStatusResponse
import com.github.nexters.ppotto.analysis.presentation.dto.CreateAnalysisRequest
import com.github.nexters.ppotto.analysis.presentation.dto.CreateAnalysisResponse
import com.github.nexters.ppotto.analysis.presentation.dto.ReissueUploadUrlsResponse
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
    private val analysisQueryService: AnalysisQueryService,
) : AnalysisApi {
    override fun create(
        @AuthenticatedUser userId: UUID,
        @Valid @RequestBody request: CreateAnalysisRequest,
    ): ApiResponse<CreateAnalysisResponse> =
        analysisService
            .createAnalysis(userId, request.boardId, request.toServiceRequests())
            .let(CreateAnalysisResponse::from)
            .let { ApiResponse.success(it) }

    override fun getActive(
        @AuthenticatedUser userId: UUID,
    ): ApiResponse<AnalysisStatusResponse?> =
        analysisQueryService
            .getActiveAnalysis(userId)
            ?.let(AnalysisStatusResponse::from)
            .let { ApiResponse.success(it) }

    override fun reissue(
        @AuthenticatedUser userId: UUID,
        @PathVariable analysisId: UUID,
    ): ApiResponse<ReissueUploadUrlsResponse> =
        analysisService
            .reissueUploadUrls(userId, analysisId)
            .let(ReissueUploadUrlsResponse::from)
            .let { ApiResponse.success(it) }

    override fun start(
        @AuthenticatedUser userId: UUID,
        @PathVariable analysisId: UUID,
    ): ApiResponse<StartUploadResponse> =
        analysisService
            .startUpload(userId, analysisId)
            .let(StartUploadResponse::from)
            .let { ApiResponse.success(it) }

    override fun get(
        @AuthenticatedUser userId: UUID,
        @PathVariable analysisId: UUID,
    ): ApiResponse<AnalysisStatusResponse> =
        analysisQueryService
            .getAnalysis(analysisId, userId)
            .let(AnalysisStatusResponse::from)
            .let { ApiResponse.success(it) }

    override fun cancel(
        @AuthenticatedUser userId: UUID,
        @PathVariable analysisId: UUID,
    ): ApiResponse<Unit> =
        analysisService
            .cancelAnalysis(userId, analysisId)
            .let { ApiResponse.success() }
}
