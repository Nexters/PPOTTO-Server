package com.github.nexters.ppotto.auth.domain

import com.github.nexters.ppotto.global.error.ErrorCode
import org.springframework.http.HttpStatus

enum class AuthErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    SOCIAL_AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "AUTH-001", "소셜 로그인 검증에 실패했습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH-002", "로그인이 만료되었습니다. 다시 로그인해 주세요."),
    APPLE_CODE_EXCHANGE_FAILED(
        HttpStatus.UNAUTHORIZED,
        "AUTH-003",
        "애플 인증 코드 교환에 실패했습니다. 다시 로그인해 주세요.",
    ),
    KAKAO_EMAIL_CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "AUTH-004", "이메일 제공에 동의해야 가입할 수 있습니다."),
    KAKAO_NICKNAME_CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "AUTH-005", "닉네임 제공에 동의해야 가입할 수 있습니다."),
    SIGNUP_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "AUTH-006", "가입에 필요한 이름이 전달되지 않았습니다."),
    SIGNUP_EMAIL_REQUIRED(HttpStatus.BAD_REQUEST, "AUTH-007", "가입에 필요한 이메일을 확인할 수 없습니다. 다시 로그인해 주세요."),
}
