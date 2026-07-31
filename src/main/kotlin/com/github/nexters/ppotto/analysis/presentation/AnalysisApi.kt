package com.github.nexters.ppotto.analysis.presentation

import com.github.nexters.ppotto.analysis.presentation.dto.AnalysisApiExamples
import com.github.nexters.ppotto.analysis.presentation.dto.AnalysisStatusResponse
import com.github.nexters.ppotto.analysis.presentation.dto.CreateAnalysisRequest
import com.github.nexters.ppotto.analysis.presentation.dto.CreateAnalysisResponse
import com.github.nexters.ppotto.analysis.presentation.dto.StartUploadResponse
import com.github.nexters.ppotto.global.openapi.ApiErrorResponse
import com.github.nexters.ppotto.global.openapi.ApiExamples
import com.github.nexters.ppotto.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import java.util.UUID
import io.swagger.v3.oas.annotations.parameters.RequestBody as OpenApiRequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

@RequestMapping("/analysis", version = "1")
@Tag(name = "분석", description = "사진 업로드와 분석 실행")
interface AnalysisApi {
    @PostMapping
    @Operation(
        summary = "분석 생성",
        description = "보드를 지정하고 사진 90~100장의 업로드 URL(만료 15분)을 한 번에 발급함",
        requestBody =
            OpenApiRequestBody(
                required = true,
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = CreateAnalysisRequest::class),
                        examples = [
                            ExampleObject(
                                name = "분석 생성 (지면상 3장, 실제 요청은 90~100장)",
                                value = AnalysisApiExamples.CREATE_ANALYSIS_REQUEST,
                            ),
                        ],
                    ),
                ],
            ),
    )
    @OpenApiResponse(
        responseCode = "200",
        useReturnTypeSchema = true,
        description = "발급 완료 (status=UPLOADING)",
        content = [
            Content(
                examples = [
                    ExampleObject(
                        name = "업로드 URL 발급",
                        value = AnalysisApiExamples.CREATE_ANALYSIS_RESPONSE,
                    ),
                ],
            ),
        ],
    )
    @OpenApiResponse(
        responseCode = "400",
        description = "요청 값이 올바르지 않음 (COMMON-001, ANALYSIS-001)",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ApiErrorResponse::class),
                examples = [
                    ExampleObject(
                        name = "COMMON-001",
                        summary = "요청 바디 검증 실패",
                        value = ApiExamples.INVALID_INPUT,
                    ),
                    ExampleObject(
                        name = "ANALYSIS-001",
                        summary = "사진 수 정책 위반 (90~100장)",
                        value = AnalysisApiExamples.PHOTO_COUNT_OUT_OF_RANGE,
                    ),
                ],
            ),
        ],
    )
    @OpenApiResponse(
        responseCode = "404",
        description = "보드를 찾을 수 없음 (BOARD-002)",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ApiErrorResponse::class),
                examples = [
                    ExampleObject(
                        name = "BOARD-002",
                        summary = "보드 없음 또는 소유자 불일치",
                        value = AnalysisApiExamples.BOARD_NOT_FOUND,
                    ),
                ],
            ),
        ],
    )
    @OpenApiResponse(
        responseCode = "409",
        description = "진행 중인 분석이 이미 있음 (ANALYSIS-002)",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ApiErrorResponse::class),
                examples = [
                    ExampleObject(
                        name = "ANALYSIS-002",
                        summary = "유저당 1개. /analysis/active로 복귀하거나 취소 후 재시도",
                        value = AnalysisApiExamples.ACTIVE_ANALYSIS_EXISTS,
                    ),
                ],
            ),
        ],
    )
    fun create(
        userId: UUID,
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
        content = [
            Content(
                examples = [
                    ExampleObject(
                        name = "분석 진행 중",
                        value = AnalysisApiExamples.ANALYZING_STATUS_RESPONSE,
                    ),
                    ExampleObject(
                        name = "진행 중 분석 없음",
                        value = AnalysisApiExamples.NO_ACTIVE_ANALYSIS_RESPONSE,
                    ),
                ],
            ),
        ],
    )
    fun getActive(userId: UUID): ApiResponse<AnalysisStatusResponse?>

    @PostMapping("/{analysisId}/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
        summary = "분석 시작",
        description = "GCS 오브젝트 존재를 확인해 없는 사진은 제외하고 분석 파이프라인을 시작함",
        parameters = [
            Parameter(
                name = "analysisId",
                description = "시작할 분석 ID (uuidv7)",
                example = "01983f2f-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
            ),
        ],
    )
    @OpenApiResponse(
        responseCode = "202",
        useReturnTypeSchema = true,
        description = "분석 시작됨",
        content = [
            Content(
                examples = [
                    ExampleObject(
                        name = "일부 사진 업로드 실패",
                        value = AnalysisApiExamples.START_UPLOAD_RESPONSE,
                    ),
                ],
            ),
        ],
    )
    @AnalysisNotFoundApiResponse
    @OpenApiResponse(
        responseCode = "409",
        description = "현재 상태와 요청이 충돌함 (ANALYSIS-003, ANALYSIS-008)",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ApiErrorResponse::class),
                examples = [
                    ExampleObject(
                        name = "ANALYSIS-003",
                        summary = "이미 시작되었거나 종료된 분석",
                        value = AnalysisApiExamples.ALREADY_STARTED_OR_FINISHED,
                    ),
                    ExampleObject(
                        name = "ANALYSIS-008",
                        summary = "업로드 완료된 사진 0장",
                        value = AnalysisApiExamples.NO_UPLOADED_PHOTOS,
                    ),
                ],
            ),
        ],
    )
    fun start(
        userId: UUID,
        analysisId: UUID,
    ): ApiResponse<StartUploadResponse>

    @GetMapping("/{analysisId}")
    @Operation(
        summary = "분석 상태 조회",
        description = "로딩 화면에서 2~3초 간격으로 폴링함. COMPLETED가 되면 보드를 다시 조회함",
        parameters = [
            Parameter(
                name = "analysisId",
                description = "조회할 분석 ID (uuidv7)",
                example = "01983f2f-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
            ),
        ],
    )
    @OpenApiResponse(
        responseCode = "200",
        useReturnTypeSchema = true,
        description = "분석 상태",
        content = [
            Content(
                examples = [
                    ExampleObject(
                        name = "분석 중",
                        value = AnalysisApiExamples.ANALYZING_STATUS_RESPONSE,
                    ),
                    ExampleObject(
                        name = "완료",
                        value = AnalysisApiExamples.COMPLETED_STATUS_RESPONSE,
                    ),
                    ExampleObject(
                        name = "실패",
                        value = AnalysisApiExamples.FAILED_STATUS_RESPONSE,
                    ),
                ],
            ),
        ],
    )
    @AnalysisNotFoundApiResponse
    fun get(
        userId: UUID,
        analysisId: UUID,
    ): ApiResponse<AnalysisStatusResponse>
}
