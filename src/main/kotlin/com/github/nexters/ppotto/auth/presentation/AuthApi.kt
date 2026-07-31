package com.github.nexters.ppotto.auth.presentation

import com.github.nexters.ppotto.auth.presentation.dto.AuthApiExamples
import com.github.nexters.ppotto.auth.presentation.dto.LoginRequest
import com.github.nexters.ppotto.auth.presentation.dto.LoginResponse
import com.github.nexters.ppotto.auth.presentation.dto.RefreshRequest
import com.github.nexters.ppotto.auth.presentation.dto.TokenPairResponse
import com.github.nexters.ppotto.global.openapi.ApiErrorResponse
import com.github.nexters.ppotto.global.openapi.EmptySuccessApiResponse
import com.github.nexters.ppotto.global.openapi.InvalidInputApiResponse
import com.github.nexters.ppotto.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import java.util.UUID
import io.swagger.v3.oas.annotations.parameters.RequestBody as OpenApiRequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

@RequestMapping("/auth", version = "1")
@Tag(name = "인증", description = "소셜 로그인과 서비스 토큰 관리")
interface AuthApi {
    @PostMapping("/login")
    @Operation(
        summary = "소셜 로그인",
        description = "카카오 또는 애플 계정을 검증하고 가입과 로그인을 함께 처리함",
        requestBody =
            OpenApiRequestBody(
                required = true,
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = LoginRequest::class),
                        examples = [
                            ExampleObject(
                                name = "카카오 로그인",
                                value = AuthApiExamples.KAKAO_LOGIN_REQUEST,
                            ),
                            ExampleObject(
                                name = "애플 최초 로그인",
                                value = AuthApiExamples.APPLE_FIRST_LOGIN_REQUEST,
                            ),
                            ExampleObject(
                                name = "애플 재로그인",
                                value = AuthApiExamples.APPLE_RELOGIN_REQUEST,
                            ),
                        ],
                    ),
                ],
            ),
    )
    @OpenApiResponse(
        responseCode = "200",
        useReturnTypeSchema = true,
        description = "로그인 성공",
        content = [
            Content(
                examples = [
                    ExampleObject(
                        name = "신규 가입 - 약관 동의 필요",
                        value = AuthApiExamples.NEW_USER_LOGIN_RESPONSE,
                    ),
                    ExampleObject(
                        name = "재로그인 - 바로 보드 진입",
                        value = AuthApiExamples.RETURNING_USER_LOGIN_RESPONSE,
                    ),
                ],
            ),
        ],
    )
    @InvalidInputApiResponse
    @OpenApiResponse(
        responseCode = "401",
        description = "소셜 로그인 검증에 실패함 (AUTH-001, AUTH-003)",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ApiErrorResponse::class),
                examples = [
                    ExampleObject(
                        name = "AUTH-001",
                        summary = "provider 토큰 검증 실패 (만료, 위조, aud/app_id 불일치, nonce 불일치)",
                        value = AuthApiExamples.SOCIAL_AUTHENTICATION_FAILED,
                    ),
                    ExampleObject(
                        name = "AUTH-003",
                        summary = "애플 authorization code 교환 실패 (만료 또는 재사용). 최초 로그인에서만 치명",
                        value = AuthApiExamples.APPLE_CODE_EXCHANGE_FAILED,
                    ),
                ],
            ),
        ],
    )
    @OpenApiResponse(
        responseCode = "403",
        description = "가입에 필요한 동의가 부족함 (AUTH-004)",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ApiErrorResponse::class),
                examples = [
                    ExampleObject(
                        name = "AUTH-004",
                        summary = "카카오 이메일 동의 필요. account_email 추가 동의 후 재시도",
                        value = AuthApiExamples.KAKAO_EMAIL_CONSENT_REQUIRED,
                    ),
                ],
            ),
        ],
    )
    fun login(request: LoginRequest): ApiResponse<LoginResponse>

    @PostMapping("/refresh")
    @Operation(
        summary = "토큰 재발급",
        description = "유효한 refresh token을 회전하고 새 토큰 쌍을 발급함",
    )
    @OpenApiResponse(
        responseCode = "200",
        useReturnTypeSchema = true,
        description = "재발급 성공",
        content = [
            Content(
                examples = [
                    ExampleObject(
                        name = "재발급 성공",
                        value = AuthApiExamples.TOKEN_PAIR_RESPONSE,
                    ),
                ],
            ),
        ],
    )
    @OpenApiResponse(
        responseCode = "401",
        description = "refresh token이 유효하지 않음 (AUTH-002)",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ApiErrorResponse::class),
                examples = [
                    ExampleObject(
                        name = "AUTH-002",
                        summary = "refresh token 만료 또는 위조. 재로그인 필요",
                        value = AuthApiExamples.INVALID_REFRESH_TOKEN,
                    ),
                ],
            ),
        ],
    )
    fun refresh(request: RefreshRequest): ApiResponse<TokenPairResponse>

    @PostMapping("/logout")
    @Operation(
        summary = "로그아웃",
        description = "현재 사용자의 refresh token 세션을 폐기함. 서버 데이터는 유지되므로 재로그인하면 그대로 복구됨",
    )
    @EmptySuccessApiResponse
    fun logout(userId: UUID): ApiResponse<Unit>
}
