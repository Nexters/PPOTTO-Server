package com.github.nexters.ppotto.analysis.domain

import com.github.nexters.ppotto.global.error.ErrorCode
import org.springframework.http.HttpStatus

enum class AnalysisErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    GROUP_COUNT_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "ANALYSIS-001", "사진 그룹은 20개에서 100개 사이여야 합니다."),
    ACTIVE_ANALYSIS_EXISTS(HttpStatus.CONFLICT, "ANALYSIS-002", "이미 진행 중인 분석이 있습니다."),
    ALREADY_STARTED_OR_FINISHED(HttpStatus.CONFLICT, "ANALYSIS-003", "이미 시작되었거나 종료된 분석입니다."),
    CANCEL_NOT_ALLOWED(HttpStatus.CONFLICT, "ANALYSIS-004", "분석이 시작되어 취소할 수 없습니다."),
    ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "ANALYSIS-005", "분석을 찾을 수 없습니다."),
    INVALID_GEMINI_RESPONSE(HttpStatus.BAD_GATEWAY, "ANALYSIS-007", "Gemini 응답이 요청한 사진 목록과 일치하지 않습니다."),
    NO_UPLOADED_PHOTOS(HttpStatus.CONFLICT, "ANALYSIS-008", "업로드된 사진이 없습니다."),
    INVALID_BURST_GROUP(HttpStatus.BAD_REQUEST, "ANALYSIS-009", "연사 그룹은 대표 사진을 정확히 1장 포함해야 합니다."),
    BURST_GROUP_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "ANALYSIS-010", "그룹당 사진은 최대 10장까지 가능합니다."),
    STICKER_BACKGROUND_REMOVAL_FAILED(HttpStatus.BAD_GATEWAY, "ANALYSIS-011", "스티커 배경 제거에 실패했습니다."),
}
