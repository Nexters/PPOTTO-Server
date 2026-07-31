package com.github.nexters.ppotto.auth.presentation

import com.github.nexters.ppotto.auth.application.AuthService
import com.github.nexters.ppotto.auth.application.port.AuthActiveUserPort
import com.github.nexters.ppotto.auth.application.port.AuthTermsPort
import com.github.nexters.ppotto.auth.application.port.AuthUserPort
import com.github.nexters.ppotto.auth.presentation.dto.LoginRequest
import com.github.nexters.ppotto.auth.presentation.dto.LoginResponse
import com.github.nexters.ppotto.auth.presentation.dto.RefreshRequest
import com.github.nexters.ppotto.auth.presentation.dto.TokenPairResponse
import com.github.nexters.ppotto.global.openapi.InvalidInputApiResponse
import com.github.nexters.ppotto.global.response.ApiResponse
import com.github.nexters.ppotto.global.security.AuthenticatedUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

@RestController
@RequestMapping("/auth", version = "1")
@ConditionalOnBean(AuthUserPort::class, AuthTermsPort::class, AuthActiveUserPort::class)
@Tag(name = "인증", description = "소셜 로그인과 서비스 토큰 관리")
class AuthController(
    private val authService: AuthService,
) {
    @PostMapping("/login")
    @Operation(
        summary = "소셜 로그인",
        description = "카카오 또는 애플 계정을 검증하고 가입과 로그인을 함께 처리함",
    )
    @InvalidInputApiResponse
    @OpenApiResponse(responseCode = "401", description = "소셜 로그인 검증에 실패함")
    @OpenApiResponse(responseCode = "403", description = "가입에 필요한 동의가 부족함")
    fun login(
        @Valid @RequestBody request: LoginRequest,
    ): ApiResponse<LoginResponse> = ApiResponse.success(LoginResponse.from(authService.login(request.toCommand())))

    @PostMapping("/refresh")
    @Operation(
        summary = "토큰 재발급",
        description = "유효한 refresh token을 회전하고 새 토큰 쌍을 발급함",
    )
    @OpenApiResponse(responseCode = "401", description = "refresh token이 유효하지 않음")
    fun refresh(
        @Valid @RequestBody request: RefreshRequest,
    ): ApiResponse<TokenPairResponse> = ApiResponse.success(TokenPairResponse.from(authService.refresh(request.refreshToken)))

    @PostMapping("/logout")
    @Operation(
        summary = "로그아웃",
        description = "현재 사용자의 refresh token 세션을 폐기함",
    )
    fun logout(
        @AuthenticatedUser userId: UUID,
    ): ApiResponse<Unit> {
        authService.logout(userId)
        return ApiResponse.success()
    }
}
