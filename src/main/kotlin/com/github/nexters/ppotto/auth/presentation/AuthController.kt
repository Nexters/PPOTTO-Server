package com.github.nexters.ppotto.auth.presentation

import com.github.nexters.ppotto.auth.application.AuthService
import com.github.nexters.ppotto.auth.application.port.AuthActiveUserPort
import com.github.nexters.ppotto.auth.application.port.AuthTermsPort
import com.github.nexters.ppotto.auth.application.port.AuthUserPort
import com.github.nexters.ppotto.auth.presentation.dto.LoginRequest
import com.github.nexters.ppotto.auth.presentation.dto.LoginResponse
import com.github.nexters.ppotto.auth.presentation.dto.RefreshRequest
import com.github.nexters.ppotto.auth.presentation.dto.TokenPairResponse
import com.github.nexters.ppotto.global.error.UnauthorizedException
import com.github.nexters.ppotto.global.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/auth", version = "1")
@ConditionalOnBean(AuthUserPort::class, AuthTermsPort::class, AuthActiveUserPort::class)
class AuthController(
    private val authService: AuthService,
) {
    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
    ): ApiResponse<LoginResponse> = ApiResponse.success(LoginResponse.from(authService.login(request.toCommand())))

    @PostMapping("/refresh")
    fun refresh(
        @Valid @RequestBody request: RefreshRequest,
    ): ApiResponse<TokenPairResponse> = ApiResponse.success(TokenPairResponse.from(authService.refresh(request.refreshToken)))

    @PostMapping("/logout")
    fun logout(
        @AuthenticationPrincipal userId: UUID?,
    ): ApiResponse<Unit> {
        authService.logout(userId ?: throw UnauthorizedException())
        return ApiResponse.success()
    }
}
