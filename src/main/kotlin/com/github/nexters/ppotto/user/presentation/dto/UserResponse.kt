package com.github.nexters.ppotto.user.presentation.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.user.domain.OAuthProvider
import com.github.nexters.ppotto.user.domain.User
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "현재 사용자 공개 정보")
data class UserResponse(
    @get:Schema(description = "사용자 ID (uuidv7)", example = "01983f2a-7c31-7b02-93d4-1f2e3d4c5b6a")
    @get:JsonProperty("id")
    val id: UserId,

    @field:Schema(description = "소셜 로그인 제공자", example = "KAKAO")
    val provider: OAuthProvider,

    @field:Schema(
        description = "항상 존재함. 애플 이메일 가리기 사용자는 private relay 주소",
        example = "ppotto@kakao.com",
    )
    val email: String,

    @field:Schema(description = "가입 시각", example = "2026-07-01T09:12:33+09:00")
    val createdAt: Instant,
) {
    companion object {
        fun from(user: User): UserResponse =
            UserResponse(
                id = user.id,
                provider = user.provider,
                email = user.email,
                createdAt = user.createdAt,
            )
    }
}
