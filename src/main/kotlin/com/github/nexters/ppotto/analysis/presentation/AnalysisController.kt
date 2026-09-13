package com.github.nexters.ppotto.analysis.presentation

import com.github.nexters.ppotto.analysis.application.AnalysisNotificationService
import com.github.nexters.ppotto.analysis.application.AnalysisQueryService
import com.github.nexters.ppotto.analysis.application.AnalysisService
import com.github.nexters.ppotto.analysis.presentation.dto.AnalysisStatusResponse
import com.github.nexters.ppotto.analysis.presentation.dto.CreateAnalysisRequest
import com.github.nexters.ppotto.analysis.presentation.dto.CreateAnalysisResponse
import com.github.nexters.ppotto.analysis.presentation.dto.ReissueUploadUrlsResponse
import com.github.nexters.ppotto.analysis.presentation.dto.StartUploadResponse
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.security.AuthenticatedUser
import com.github.nexters.ppotto.global.web.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class AnalysisController(
    private val analysisService: AnalysisService,
    private val analysisQueryService: AnalysisQueryService,
    private val analysisNotificationService: AnalysisNotificationService,
) : AnalysisApi {
    override fun create(
        @AuthenticatedUser userId: UserId,
        @Valid @RequestBody request: CreateAnalysisRequest,
    ): ApiResponse<CreateAnalysisResponse> =
        analysisService
            .createAnalysis(userId, request.boardId, request.toCommand())
            .let(CreateAnalysisResponse::from)
            .let { ApiResponse.success(it) }

    override fun getActive(
        @AuthenticatedUser userId: UserId,
    ): ApiResponse<AnalysisStatusResponse?> =
        analysisQueryService
            .getActiveAnalysis(userId)
            ?.let(AnalysisStatusResponse::from)
            .let { ApiResponse.success(it) }

    override fun reissue(
        @AuthenticatedUser userId: UserId,
        @PathVariable analysisId: AnalysisId,
    ): ApiResponse<ReissueUploadUrlsResponse> =
        analysisService
            .reissueUploadUrls(userId, analysisId)
            .let(ReissueUploadUrlsResponse::from)
            .let { ApiResponse.success(it) }

    override fun start(
        @AuthenticatedUser userId: UserId,
        @PathVariable analysisId: AnalysisId,
    ): ApiResponse<StartUploadResponse> =
        analysisService
            .startUpload(userId, analysisId)
            .let(StartUploadResponse::from)
            .let { ApiResponse.success(it) }

    override fun get(
        @AuthenticatedUser userId: UserId,
        @PathVariable analysisId: AnalysisId,
    ): ApiResponse<AnalysisStatusResponse> =
        analysisQueryService
            .getAnalysis(analysisId, userId)
            .let(AnalysisStatusResponse::from)
            .let { ApiResponse.success(it) }

    override fun requestCompletionNotification(
        @AuthenticatedUser userId: UserId,
        @PathVariable analysisId: AnalysisId,
    ): ApiResponse<Unit> {
        analysisNotificationService.requestCompletionNotification(userId, analysisId)
        return ApiResponse.success()
    }

    override fun cancel(
        @AuthenticatedUser userId: UserId,
        @PathVariable analysisId: AnalysisId,
    ): ApiResponse<Unit> {
        analysisService.cancelAnalysis(userId, analysisId)
        return ApiResponse.success()
    }
}
