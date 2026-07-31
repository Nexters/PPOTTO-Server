package com.github.nexters.ppotto.sticker.domain

import com.github.nexters.ppotto.global.error.ErrorCode
import org.springframework.http.HttpStatus

enum class StickerErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    STICKER_NOT_FOUND(HttpStatus.NOT_FOUND, "STICKER-001", "스티커를 찾을 수 없습니다."),
}
