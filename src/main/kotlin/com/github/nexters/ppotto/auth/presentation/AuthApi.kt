package com.github.nexters.ppotto.auth.presentation

import com.github.nexters.ppotto.auth.presentation.dto.LoginRequest
import com.github.nexters.ppotto.auth.presentation.dto.LoginResponse
import com.github.nexters.ppotto.auth.presentation.dto.RefreshRequest
import com.github.nexters.ppotto.auth.presentation.dto.TokenPairResponse
import com.github.nexters.ppotto.auth.presentation.dto.WebLoginRequest
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.openapi.EmptySuccessApiResponse
import com.github.nexters.ppotto.global.openapi.InvalidInputApiResponse
import com.github.nexters.ppotto.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

@RequestMapping("/auth", version = "1+")
@Tag(name = "인증", description = "소셜 로그인과 서비스 토큰 관리")
interface AuthApi {
    @PostMapping("/login")
    @Operation(
        operationId = "login",
        summary = "소셜 로그인",
        description = "카카오 또는 애플 계정을 검증하고 가입과 로그인을 함께 처리함",
    )
    @OpenApiResponse(
        responseCode = "200",
        useReturnTypeSchema = true,
        description = "로그인 성공",
    )
    @SignupInvalidInputApiResponse
    @LoginUnauthorizedApiResponse
    @ConsentRequiredApiResponse
    fun login(request: LoginRequest): ApiResponse<LoginResponse>

    @PostMapping("/login/web")
    @Operation(
        operationId = "webLogin",
        summary = "웹 소셜 로그인 (가입 겸용)",
        description =
            "브라우저가 provider 인가 페이지를 거쳐 받은 authorization code를 서버가 토큰으로 교환해 로그인함. " +
                "앱 로그인과 같은 계정으로 이어지며 현재 KAKAO만 지원함",
    )
    @OpenApiResponse(
        responseCode = "200",
        useReturnTypeSchema = true,
        description = "로그인 성공",
    )
    @InvalidInputApiResponse
    @WebLoginUnauthorizedApiResponse
    @ConsentRequiredApiResponse
    fun webLogin(request: WebLoginRequest): ApiResponse<LoginResponse>

    @PostMapping("/refresh")
    @Operation(
        operationId = "refresh",
        summary = "토큰 재발급",
        description = "유효한 refresh token을 회전하고 새 토큰 쌍을 발급함",
    )
    @OpenApiResponse(
        responseCode = "200",
        useReturnTypeSchema = true,
        description = "재발급 성공",
    )
    @InvalidRefreshTokenApiResponse
    fun refresh(request: RefreshRequest): ApiResponse<TokenPairResponse>

    @PostMapping("/logout")
    @Operation(
        operationId = "logout",
        summary = "로그아웃",
        description = "현재 사용자의 refresh token 세션을 폐기함. 서버 데이터는 유지되므로 재로그인하면 그대로 복구됨",
    )
    @EmptySuccessApiResponse
    fun logout(userId: UserId): ApiResponse<Unit>
}
