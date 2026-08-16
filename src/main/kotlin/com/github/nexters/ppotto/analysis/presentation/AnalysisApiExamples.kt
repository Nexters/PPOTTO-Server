package com.github.nexters.ppotto.analysis.presentation

import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import com.github.nexters.ppotto.analysis.presentation.dto.AnalysisStatusResponse
import com.github.nexters.ppotto.analysis.presentation.dto.CreateAnalysisRequest
import com.github.nexters.ppotto.analysis.presentation.dto.CreateAnalysisResponse
import com.github.nexters.ppotto.analysis.presentation.dto.PhotoUploadContentType
import com.github.nexters.ppotto.analysis.presentation.dto.PhotoUploadGroup
import com.github.nexters.ppotto.analysis.presentation.dto.PhotoUploadItem
import com.github.nexters.ppotto.analysis.presentation.dto.PhotoUploadUrlItem
import com.github.nexters.ppotto.analysis.presentation.dto.ReissueUploadUrlsResponse
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
        name = "분석 생성 (지면상 3그룹, 실제 요청은 20~100그룹). 두 번째 그룹은 연사 2장 예시",
        value =
            CreateAnalysisRequest(
                boardId = BOARD_ID,
                photos =
                    listOf(
                        PhotoUploadGroup(
                            items =
                                listOf(
                                    PhotoUploadItem(
                                        takenAt = Instant.parse("2026-06-14T04:22:10Z"),
                                        contentType = PhotoUploadContentType.JPEG,
                                        isRepresentative = true,
                                    ),
                                ),
                        ),
                        PhotoUploadGroup(
                            items =
                                listOf(
                                    PhotoUploadItem(
                                        takenAt = Instant.parse("2026-06-14T04:24:02Z"),
                                        contentType = PhotoUploadContentType.PNG,
                                        isRepresentative = true,
                                    ),
                                    PhotoUploadItem(
                                        takenAt = Instant.parse("2026-06-14T04:24:03Z"),
                                        contentType = PhotoUploadContentType.WEBP,
                                        isRepresentative = false,
                                    ),
                                ),
                        ),
                        PhotoUploadGroup(
                            items =
                                listOf(
                                    PhotoUploadItem(
                                        takenAt = Instant.parse("2026-07-02T10:05:44Z"),
                                        contentType = PhotoUploadContentType.JPEG,
                                        isRepresentative = true,
                                    ),
                                ),
                        ),
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
                                uploadUrl = "https://storage.googleapis.com/ppotto-photos/01983f2f.webp?X-Goog-Expires=900",
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

private val REISSUE_UPLOAD_URLS_RESPONSE =
    ApiExample(
        name = "재발급된 업로드 URL",
        value =
            ApiResponse.success(
                ReissueUploadUrlsResponse(
                    uploads =
                        listOf(
                            PhotoUploadUrlItem(
                                photoId = UUID.fromString("01983f2e-1a2b-7c3d-8e4f-5a6b7c8d9e0f"),
                                uploadUrl = "https://storage.googleapis.com/ppotto-photos/01983f2e.jpg?X-Goog-Expires=900",
                            ),
                        ),
                ),
            ),
    )

private val BOARD_NOT_FOUND =
    ApiExamples.errorExample(
        code = "BOARD-002",
        summary = "보드 없음 또는 소유자 불일치",
        message = "보드를 찾을 수 없습니다.",
    )

private val GROUP_COUNT_OUT_OF_RANGE =
    ApiExamples.errorExample(
        code = "ANALYSIS-001",
        summary = "사진 그룹 수 정책 위반 (20~100개)",
        message = "사진 그룹은 20개에서 100개 사이여야 합니다.",
    )

private val INVALID_BURST_GROUP =
    ApiExamples.errorExample(
        code = "ANALYSIS-009",
        summary = "연사 그룹 내 대표 사진이 정확히 1장이 아님",
        message = "연사 그룹은 대표 사진을 정확히 1장 포함해야 합니다.",
    )

private val BURST_GROUP_SIZE_EXCEEDED =
    ApiExamples.errorExample(
        code = "ANALYSIS-010",
        summary = "그룹당 사진이 10장을 초과함",
        message = "그룹당 사진은 최대 10장까지 가능합니다.",
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

private val CANCEL_NOT_ALLOWED =
    ApiExamples.errorExample(
        code = "ANALYSIS-004",
        summary = "UPLOADING이 아닌 분석은 취소 불가",
        message = "분석이 시작되어 취소할 수 없습니다.",
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
                            "400" to
                                listOf(
                                    ApiExamples.INVALID_INPUT,
                                    GROUP_COUNT_OUT_OF_RANGE,
                                    INVALID_BURST_GROUP,
                                    BURST_GROUP_SIZE_EXCEEDED,
                                ),
                            "404" to listOf(BOARD_NOT_FOUND),
                            "409" to listOf(ACTIVE_ANALYSIS_EXISTS),
                        ),
                ),
            AnalysisApi::getActive to
                OperationExamples(
                    responses = mapOf("200" to listOf(ANALYZING_STATUS_RESPONSE, NO_ACTIVE_ANALYSIS_RESPONSE)),
                ),
            AnalysisApi::reissue to
                OperationExamples(
                    responses =
                        mapOf(
                            "200" to listOf(REISSUE_UPLOAD_URLS_RESPONSE),
                            "404" to ANALYSIS_NOT_FOUND_RESPONSE,
                            "409" to listOf(ALREADY_STARTED_OR_FINISHED),
                        ),
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
            AnalysisApi::cancel to
                OperationExamples(
                    responses =
                        mapOf(
                            "200" to ApiExamples.EMPTY_SUCCESS,
                            "404" to ANALYSIS_NOT_FOUND_RESPONSE,
                            "409" to listOf(CANCEL_NOT_ALLOWED),
                        ),
                ),
        )
}
