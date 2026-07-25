package com.github.nexters.ppotto.global.error

import org.springframework.http.HttpStatus

interface ErrorCode {
    val status: HttpStatus
    val code: String
    val message: String
}
