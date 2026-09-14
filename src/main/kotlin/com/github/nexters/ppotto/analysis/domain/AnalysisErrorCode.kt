package com.github.nexters.ppotto.analysis.domain

import com.fasterxml.jackson.annotation.JsonValue
import com.github.nexters.ppotto.global.error.ErrorCode
import org.springframework.http.HttpStatus

enum class AnalysisErrorCode(
    override val status: HttpStatus,

    @get:JsonValue
    override val code: String,

    override val message: String,
) : ErrorCode {
    GROUP_COUNT_OUT_OF_RANGE(
        HttpStatus.BAD_REQUEST,
        "ANALYSIS-001",
        "사진 그룹은 ${Analysis.MIN_PHOTO_GROUP_COUNT}개에서 ${Analysis.MAX_PHOTO_GROUP_COUNT}개 사이여야 합니다.",
    ),
    ACTIVE_ANALYSIS_EXISTS(HttpStatus.CONFLICT, "ANALYSIS-002", "이미 진행 중인 분석이 있습니다."),
    ALREADY_STARTED_OR_FINISHED(HttpStatus.CONFLICT, "ANALYSIS-003", "이미 시작되었거나 종료된 분석입니다."),
    CANCEL_NOT_ALLOWED(HttpStatus.CONFLICT, "ANALYSIS-004", "분석이 시작되어 취소할 수 없습니다."),
    ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "ANALYSIS-005", "분석을 찾을 수 없습니다."),
    INVALID_GEMINI_RESPONSE(HttpStatus.BAD_GATEWAY, "ANALYSIS-007", "Gemini 응답이 요청한 사진 목록과 일치하지 않습니다."),
    NO_UPLOADED_PHOTOS(HttpStatus.CONFLICT, "ANALYSIS-008", "업로드된 사진이 없습니다."),
    INVALID_BURST_GROUP(HttpStatus.BAD_REQUEST, "ANALYSIS-009", "연사 그룹은 대표 사진을 정확히 1장 포함해야 합니다."),
    BURST_GROUP_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "ANALYSIS-010", "그룹당 사진은 최대 ${Photo.MAX_BURST_GROUP_SIZE}장까지 가능합니다."),
    STICKER_BACKGROUND_REMOVAL_FAILED(HttpStatus.BAD_GATEWAY, "ANALYSIS-011", "스티커 배경 제거에 실패했습니다."),
    NO_STICKER_SUBJECT(HttpStatus.UNPROCESSABLE_ENTITY, "ANALYSIS-012", "스티커로 만들 대상을 찾지 못했습니다."),
    STICKER_GENERATION_FAILED(HttpStatus.BAD_GATEWAY, "ANALYSIS-013", "스티커를 생성하지 못했습니다."),
    RESULT_SAVE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "ANALYSIS-014", "분석 결과를 저장하지 못했습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "ANALYSIS-015", "분석 처리 중 오류가 발생했습니다."),
    ANALYSIS_CANCELED(HttpStatus.CONFLICT, "ANALYSIS-016", "분석을 취소했습니다."),
    CLASSIFICATION_FAILED(HttpStatus.BAD_GATEWAY, "ANALYSIS-017", "사진 분석에 실패했습니다."),
    NOTIFICATION_REQUEST_NOT_ALLOWED(
        HttpStatus.CONFLICT,
        "ANALYSIS-018",
        "진행 중인 분석에만 결과 알림을 신청할 수 있습니다.",
    ),
}
