package com.github.nexters.ppotto.terms.domain

import com.github.nexters.ppotto.global.error.ErrorCode
import org.springframework.http.HttpStatus

enum class TermErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    REQUIRED_TERMS_MISSING(HttpStatus.BAD_REQUEST, "TERM-001", "필수 약관에 동의해야 합니다."),
}
