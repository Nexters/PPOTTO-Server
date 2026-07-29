package com.github.nexters.ppotto.analysis.domain

import com.github.nexters.ppotto.global.error.ErrorCode
import org.springframework.http.HttpStatus

enum class AnalysisErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    ALREADY_STARTED_OR_FINISHED(HttpStatus.CONFLICT, "ANALYSIS-003", "이미 시작되었거나 종료된 분석입니다."),
}
