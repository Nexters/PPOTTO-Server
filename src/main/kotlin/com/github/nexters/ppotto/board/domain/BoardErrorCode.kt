package com.github.nexters.ppotto.board.domain

import com.github.nexters.ppotto.global.error.ErrorCode
import org.springframework.http.HttpStatus

enum class BoardErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "BOARD-002", "보드를 찾을 수 없습니다."),
}
