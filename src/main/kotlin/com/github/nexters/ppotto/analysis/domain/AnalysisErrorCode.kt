package com.github.nexters.ppotto.analysis.domain

import com.github.nexters.ppotto.global.error.ErrorCode
import org.springframework.http.HttpStatus

enum class AnalysisErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    PHOTO_COUNT_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "ANALYSIS-001", "사진은 90장에서 100장 사이여야 합니다."),
    ACTIVE_ANALYSIS_EXISTS(HttpStatus.CONFLICT, "ANALYSIS-002", "이미 진행 중인 분석이 있습니다."),
    ALREADY_STARTED_OR_FINISHED(HttpStatus.CONFLICT, "ANALYSIS-003", "이미 시작되었거나 종료된 분석입니다."),
    ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "ANALYSIS-005", "분석을 찾을 수 없습니다."),
    INVALID_GEMINI_RESPONSE(HttpStatus.BAD_GATEWAY, "ANALYSIS-007", "Gemini 응답이 요청한 사진 목록과 일치하지 않습니다."),
    NO_UPLOADED_PHOTOS(HttpStatus.CONFLICT, "ANALYSIS-008", "업로드된 사진이 없습니다."),
}
