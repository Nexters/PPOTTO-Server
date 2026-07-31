package com.github.nexters.ppotto.auth.presentation

import com.github.nexters.ppotto.auth.application.AuthService
import com.github.nexters.ppotto.auth.application.port.AuthActiveUserPort
import com.github.nexters.ppotto.auth.application.port.AuthTermsPort
import com.github.nexters.ppotto.auth.application.port.AuthUserPort
import com.github.nexters.ppotto.auth.presentation.dto.LoginRequest
import com.github.nexters.ppotto.auth.presentation.dto.LoginResponse
import com.github.nexters.ppotto.auth.presentation.dto.RefreshRequest
import com.github.nexters.ppotto.auth.presentation.dto.TokenPairResponse
import com.github.nexters.ppotto.global.response.ApiResponse
import com.github.nexters.ppotto.global.security.AuthenticatedUser
import jakarta.validation.Valid
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@ConditionalOnBean(AuthUserPort::class, AuthTermsPort::class, AuthActiveUserPort::class)
class AuthController(
    private val authService: AuthService,
) : AuthApi {
    override fun login(
        @Valid @RequestBody request: LoginRequest,
    ): ApiResponse<LoginResponse> =
        authService
            .login(request.toCommand())
            .let(LoginResponse::from)
            .let { ApiResponse.success(it) }

    override fun refresh(
        @Valid @RequestBody request: RefreshRequest,
    ): ApiResponse<TokenPairResponse> =
        authService
            .refresh(request.refreshToken)
            .let(TokenPairResponse::from)
            .let { ApiResponse.success(it) }

    override fun logout(
        @AuthenticatedUser userId: UUID,
    ): ApiResponse<Unit> =
        authService
            .logout(userId)
            .let { ApiResponse.success() }
}
