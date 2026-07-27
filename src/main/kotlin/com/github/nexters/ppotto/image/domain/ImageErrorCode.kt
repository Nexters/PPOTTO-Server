package com.github.nexters.ppotto.image.domain

import com.github.nexters.ppotto.global.error.ErrorCode
import org.springframework.http.HttpStatus

enum class ImageErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    UNSUPPORTED_FILE_EXTENSION(HttpStatus.BAD_REQUEST, "IMAGE-001", "지원하지 않는 파일 확장자입니다."),
    ALREADY_PROCESSED_UPLOAD(HttpStatus.CONFLICT, "IMAGE-002", "이미 처리된 업로드입니다."),
}
