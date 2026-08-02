package com.github.nexters.ppotto.analysis.presentation

import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.analysis.presentation.dto.AnalysisStatusResponse
import com.github.nexters.ppotto.analysis.presentation.dto.CreateAnalysisRequest
import com.github.nexters.ppotto.analysis.presentation.dto.CreateAnalysisResponse
import com.github.nexters.ppotto.analysis.presentation.dto.PhotoUploadItem
import com.github.nexters.ppotto.analysis.presentation.dto.PhotoUploadUrlItem
import com.github.nexters.ppotto.analysis.presentation.dto.StartUploadResponse
import com.github.nexters.ppotto.global.openapi.ApiExample
import com.github.nexters.ppotto.global.openapi.ApiExampleProvider
import com.github.nexters.ppotto.global.openapi.ApiExamples
import com.github.nexters.ppotto.global.openapi.OperationExamples
import com.github.nexters.ppotto.global.response.ApiResponse
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import kotlin.reflect.KFunction

private val ANALYSIS_ID = UUID.fromString("01983f2f-1a2b-7c3d-8e4f-5a6b7c8d9e0f")
private val BOARD_ID = UUID.fromString("01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b")
private val STARTED_AT = Instant.parse("2026-07-27T05:02:11Z")

private val ANALYZING_STATUS =
    AnalysisStatusResponse(
        id = ANALYSIS_ID,
        boardId = BOARD_ID,
        status = AnalysisStatus.ANALYZING,
        progress = 45,
        failedReason = null,
        startedAt = STARTED_AT,
        completedAt = null,
    )

private val CREATE_ANALYSIS_REQUEST =
    ApiExample(
        name = "분석 생성 (지면상 3장, 실제 요청은 90~100장)",
        value =
            CreateAnalysisRequest(
                boardId = BOARD_ID,
                photos =
                    listOf(
                        PhotoUploadItem(takenAt = Instant.parse("2026-06-14T04:22:10Z"), contentType = PhotoContentType.JPEG),
                        PhotoUploadItem(takenAt = Instant.parse("2026-06-14T04:24:02Z"), contentType = PhotoContentType.HEIC),
                        PhotoUploadItem(takenAt = Instant.parse("2026-07-02T10:05:44Z"), contentType = PhotoContentType.JPEG),
                    ),
            ),
    )

private val CREATE_ANALYSIS_RESPONSE =
    ApiExample(
        name = "업로드 URL 발급",
        value =
            ApiResponse.success(
                CreateAnalysisResponse(
                    analysisId = ANALYSIS_ID,
                    uploads =
                        listOf(
                            PhotoUploadUrlItem(
                                photoId = UUID.fromString("01983f2e-1a2b-7c3d-8e4f-5a6b7c8d9e0f"),
                                uploadUrl = "https://storage.googleapis.com/ppotto-photos/01983f2e.jpg?X-Goog-Expires=900",
                            ),
                            PhotoUploadUrlItem(
                                photoId = UUID.fromString("01983f2e-2b3c-7d4e-9f5a-6b7c8d9e0f1a"),
                                uploadUrl = "https://storage.googleapis.com/ppotto-photos/01983f2f.heic?X-Goog-Expires=900",
                            ),
                        ),
                ),
            ),
    )

private val ANALYZING_STATUS_RESPONSE = ApiExample(name = "분석 진행 중", value = ApiResponse.success(ANALYZING_STATUS))

private val ANALYZING_DETAIL_RESPONSE = ANALYZING_STATUS_RESPONSE.copy(name = "분석 중")

private val COMPLETED_STATUS_RESPONSE =
    ApiExample(
        name = "완료",
        value =
            ApiResponse.success(
                ANALYZING_STATUS.copy(
                    status = AnalysisStatus.COMPLETED,
                    progress = 100,
                    completedAt = Instant.parse("2026-07-27T05:03:38Z"),
                ),
            ),
    )

private val FAILED_STATUS_RESPONSE =
    ApiExample(
        name = "실패",
        value =
            ApiResponse.success(
                ANALYZING_STATUS.copy(
                    status = AnalysisStatus.FAILED,
                    progress = 60,
                    failedReason = "AI 분석 호출이 반복 실패했습니다.",
                ),
            ),
    )

private val NO_ACTIVE_ANALYSIS_RESPONSE =
    ApiExample(
        name = "진행 중 분석 없음",
        value = ApiResponse.success<AnalysisStatusResponse?>(null),
    )

private val START_UPLOAD_RESPONSE =
    ApiExample(
        name = "일부 사진 업로드 실패",
        value =
            ApiResponse.success(
                StartUploadResponse(
                    uploadedCount = 97,
                    failedCount = 1,
                    failedPhotoIds = listOf(UUID.fromString("01983f2e-9f8e-7d6c-b5a4-3c2b1a0f9e8d")),
                ),
            ),
    )

private val BOARD_NOT_FOUND =
    ApiExamples.errorExample(
        code = "BOARD-002",
        summary = "보드 없음 또는 소유자 불일치",
        message = "보드를 찾을 수 없습니다.",
    )

private val PHOTO_COUNT_OUT_OF_RANGE =
    ApiExamples.errorExample(
        code = "ANALYSIS-001",
        summary = "사진 수 정책 위반 (90~100장)",
        message = "사진은 90장에서 100장 사이여야 합니다.",
    )

private val ACTIVE_ANALYSIS_EXISTS =
    ApiExamples.errorExample(
        code = "ANALYSIS-002",
        summary = "유저당 1개. /analysis/active로 복귀하거나 취소 후 재시도",
        message = "이미 진행 중인 분석이 있습니다.",
    )

private val ALREADY_STARTED_OR_FINISHED =
    ApiExamples.errorExample(
        code = "ANALYSIS-003",
        summary = "이미 시작되었거나 종료된 분석",
        message = "이미 시작되었거나 종료된 분석입니다.",
    )

private val NO_UPLOADED_PHOTOS =
    ApiExamples.errorExample(
        code = "ANALYSIS-008",
        summary = "업로드 완료된 사진 0장",
        message = "업로드된 사진이 없습니다.",
    )

private val ANALYSIS_NOT_FOUND_RESPONSE =
    listOf(
        ApiExamples.errorExample(
            code = "ANALYSIS-005",
            summary = "분석 없음 또는 소유자 불일치",
            message = "분석을 찾을 수 없습니다.",
        ),
    )

@Component
class AnalysisApiExamples : ApiExampleProvider {
    override val examples: Map<KFunction<*>, OperationExamples> =
        mapOf(
            AnalysisApi::create to
                OperationExamples(
                    request = listOf(CREATE_ANALYSIS_REQUEST),
                    responses =
                        mapOf(
                            "200" to listOf(CREATE_ANALYSIS_RESPONSE),
                            "400" to listOf(ApiExamples.INVALID_INPUT, PHOTO_COUNT_OUT_OF_RANGE),
                            "404" to listOf(BOARD_NOT_FOUND),
                            "409" to listOf(ACTIVE_ANALYSIS_EXISTS),
                        ),
                ),
            AnalysisApi::getActive to
                OperationExamples(
                    responses = mapOf("200" to listOf(ANALYZING_STATUS_RESPONSE, NO_ACTIVE_ANALYSIS_RESPONSE)),
                ),
            AnalysisApi::start to
                OperationExamples(
                    responses =
                        mapOf(
                            "202" to listOf(START_UPLOAD_RESPONSE),
                            "404" to ANALYSIS_NOT_FOUND_RESPONSE,
                            "409" to listOf(ALREADY_STARTED_OR_FINISHED, NO_UPLOADED_PHOTOS),
                        ),
                ),
            AnalysisApi::get to
                OperationExamples(
                    responses =
                        mapOf(
                            "200" to
                                listOf(ANALYZING_DETAIL_RESPONSE, COMPLETED_STATUS_RESPONSE, FAILED_STATUS_RESPONSE),
                            "404" to ANALYSIS_NOT_FOUND_RESPONSE,
                        ),
                ),
        )
}
