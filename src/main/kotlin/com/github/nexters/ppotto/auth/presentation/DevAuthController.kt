package com.github.nexters.ppotto.auth.presentation

import com.github.nexters.ppotto.auth.application.port.RefreshTokenStore
import com.github.nexters.ppotto.auth.application.port.TokenProvider
import com.github.nexters.ppotto.auth.presentation.dto.TokenPairResponse
import com.github.nexters.ppotto.global.error.UnauthorizedException
import com.github.nexters.ppotto.global.response.ApiResponse
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 브라우저 단독 웹 개발용 로그인. 네이티브 카카오 로그인 없이 기존 카카오 가입 유저의 실제 토큰을 발급한다.
 * `auth.dev-login.enabled=true`(dev 서버 전용)일 때만 bean이 등록되며, 운영에는 endpoint 자체가 존재하지 않는다.
 */
@RestController
@ConditionalOnProperty("auth.dev-login.enabled", havingValue = "true")
@RequestMapping("/dev/auth", version = "1")
@Tag(name = "개발용 인증", description = "개발 환경 전용. 카카오 가입 이메일로 실제 서비스 토큰을 발급함")
class DevAuthController(
    private val tokenProvider: TokenProvider,
    private val refreshTokenStore: RefreshTokenStore,
    // ponytail: dev 전용 기능이라 별도 port 없이 user 모듈 repository를 직접 사용함
    private val userRepository: UserRepository,
) {
    @PostMapping("/login")
    @Operation(
        summary = "개발용 로그인",
        description = "카카오로 가입된 이메일과 고정 비밀번호로 해당 유저의 실제 토큰 쌍을 발급함. 이후 API 동작은 소셜 로그인과 동일",
    )
    fun login(
        @Valid @RequestBody request: DevLoginRequest,
    ): ApiResponse<TokenPairResponse> {
        if (request.password != DEV_PASSWORD) throw UnauthorizedException()
        val user = userRepository.findActiveKakaoByEmail(request.email) ?: throw UnauthorizedException()
        return tokenProvider
            .issue(user.id)
            .also { refreshTokenStore.save(user.id, it.refreshToken) }
            .let { ApiResponse.success(TokenPairResponse.from(it)) }
    }

    companion object {
        // ponytail: 고정 공용 비밀번호. dev 서버에서만 bean이 뜨므로 운영 노출 없음
        private const val DEV_PASSWORD = "password1!"
    }
}

@Schema(description = "개발용 로그인 요청")
data class DevLoginRequest(
    @field:NotBlank
    @field:Schema(description = "카카오로 가입한 계정의 이메일", example = "dev@ppotto.co.kr")
    val email: String,

    @field:NotBlank
    @field:Schema(description = "개발용 고정 비밀번호")
    val password: String,
)
