package com.github.nexters.ppotto.analysis.presentation

import com.github.nexters.ppotto.analysis.presentation.dto.AnalysisStatusResponse
import com.github.nexters.ppotto.analysis.presentation.dto.CreateAnalysisRequest
import com.github.nexters.ppotto.analysis.presentation.dto.CreateAnalysisResponse
import com.github.nexters.ppotto.analysis.presentation.dto.ReissueUploadUrlsResponse
import com.github.nexters.ppotto.analysis.presentation.dto.StartUploadResponse
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.openapi.EmptySuccessApiResponse
import com.github.nexters.ppotto.global.web.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

private const val ANALYSIS_ID_DESCRIPTION = "분석 ID (uuidv7)"
private const val ANALYSIS_ID_EXAMPLE = "01983f2f-1a2b-7c3d-8e4f-5a6b7c8d9e0f"

@RequestMapping("/analysis", version = "1+")
@Tag(name = "분석", description = "사진 업로드와 분석 실행")
interface AnalysisApi {
    @PostMapping
    @Operation(
        summary = "분석 생성",
        description = "보드를 지정하고 사진 그룹을 펼친 총 90~100장의 업로드 URL(만료 15분)을 한 번에 발급함",
    )
    @OpenApiResponse(
        responseCode = "200",
        useReturnTypeSchema = true,
        description = "발급 완료 (status=UPLOADING)",
    )
    @CreateAnalysisInvalidInputApiResponse
    @AnalysisBoardNotFoundApiResponse
    @ActiveAnalysisExistsApiResponse
    fun create(
        userId: UserId,
        request: CreateAnalysisRequest,
    ): ApiResponse<CreateAnalysisResponse>

    @GetMapping("/active")
    @Operation(
        summary = "진행 중 분석 조회",
        description = "앱 재진입 또는 분석 생성 충돌 이후 복구할 진행 중 분석을 조회함. 없으면 data가 null",
    )
    @OpenApiResponse(
        responseCode = "200",
        useReturnTypeSchema = true,
        description = "진행 중 분석 또는 null",
    )
    fun getActive(userId: UserId): ApiResponse<AnalysisStatusResponse?>

    @PostMapping("/{analysisId}/reissue")
    @Operation(
        summary = "업로드 URL 재발급",
        description =
            "분석 생성 응답을 유실했거나 업로드 URL(15분)이 만료됐을 때 호출함. " +
                "PENDING 사진의 URL만 재발급하며 UPLOADING 상태에서만 사용 가능",
        parameters = [Parameter(name = "analysisId", description = ANALYSIS_ID_DESCRIPTION, example = ANALYSIS_ID_EXAMPLE)],
    )
    @OpenApiResponse(
        responseCode = "200",
        useReturnTypeSchema = true,
        description = "재발급된 URL 목록",
    )
    @AnalysisNotFoundApiResponse
    @AnalysisAlreadyStartedApiResponse
    fun reissue(
        userId: UserId,
        analysisId: AnalysisId,
    ): ApiResponse<ReissueUploadUrlsResponse>

    @PostMapping("/{analysisId}/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
        summary = "분석 시작",
        description = "GCS 오브젝트 존재를 확인해 없는 사진은 제외하고 분석 파이프라인을 시작함",
        parameters = [Parameter(name = "analysisId", description = ANALYSIS_ID_DESCRIPTION, example = ANALYSIS_ID_EXAMPLE)],
    )
    @OpenApiResponse(
        responseCode = "202",
        useReturnTypeSchema = true,
        description = "분석 시작됨",
    )
    @AnalysisNotFoundApiResponse
    @AnalysisStartConflictApiResponse
    fun start(
        userId: UserId,
        analysisId: AnalysisId,
    ): ApiResponse<StartUploadResponse>

    @GetMapping("/{analysisId}")
    @Operation(
        summary = "분석 상태 조회",
        description = "로딩 화면에서 2~3초 간격으로 폴링함. COMPLETED가 되면 보드를 다시 조회함",
        parameters = [Parameter(name = "analysisId", description = ANALYSIS_ID_DESCRIPTION, example = ANALYSIS_ID_EXAMPLE)],
    )
    @OpenApiResponse(
        responseCode = "200",
        useReturnTypeSchema = true,
        description = "분석 상태",
    )
    @AnalysisNotFoundApiResponse
    fun get(
        userId: UserId,
        analysisId: AnalysisId,
    ): ApiResponse<AnalysisStatusResponse>

    @PostMapping("/{analysisId}/notifications")
    @Operation(
        operationId = "requestAnalysisCompletionNotification",
        summary = "분석 완료 알림 신청",
        description = "현재 진행 중인 분석의 완료 또는 실패 결과 푸시 알림을 분석별로 신청합니다. 같은 분석에 대한 재요청은 동일하게 성공합니다.",
        parameters = [
            Parameter(
                name = "analysisId",
                description = ANALYSIS_ID_DESCRIPTION,
                example = ANALYSIS_ID_EXAMPLE,
            ),
        ],
    )
    @EmptySuccessApiResponse
    @AnalysisNotFoundApiResponse
    @AnalysisNotificationRequestNotAllowedApiResponse
    fun requestCompletionNotification(
        userId: UserId,
        analysisId: AnalysisId,
    ): ApiResponse<Unit>

    @DeleteMapping("/{analysisId}/notifications")
    @Operation(
        operationId = "cancelAnalysisCompletionNotification",
        summary = "분석 완료 알림 신청 취소",
        description = "현재 진행 중인 분석의 완료 또는 실패 결과 알림 신청을 분석별로 취소합니다. 같은 분석에 대한 재취소 요청도 동일하게 성공합니다.",
        parameters = [
            Parameter(
                name = "analysisId",
                description = ANALYSIS_ID_DESCRIPTION,
                example = ANALYSIS_ID_EXAMPLE,
            ),
        ],
    )
    @EmptySuccessApiResponse
    @AnalysisNotFoundApiResponse
    @AnalysisNotificationRequestNotAllowedApiResponse
    fun cancelCompletionNotification(
        userId: UserId,
        analysisId: AnalysisId,
    ): ApiResponse<Unit>

    @DeleteMapping("/{analysisId}")
    @Operation(
        summary = "분석 취소",
        description = "업로드 중(UPLOADING)인 분석을 취소함. 분석과 사진 상태를 FAILED로 닫고, 업로드된 원본 이미지는 커밋 후 비동기로 정리함",
        parameters = [Parameter(name = "analysisId", description = ANALYSIS_ID_DESCRIPTION, example = ANALYSIS_ID_EXAMPLE)],
    )
    @EmptySuccessApiResponse
    @AnalysisNotFoundApiResponse
    @AnalysisCancelNotAllowedApiResponse
    fun cancel(
        userId: UserId,
        analysisId: AnalysisId,
    ): ApiResponse<Unit>
}
