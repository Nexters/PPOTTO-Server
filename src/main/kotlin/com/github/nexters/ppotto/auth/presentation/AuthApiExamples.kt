package com.github.nexters.ppotto.auth.presentation

import com.github.nexters.ppotto.auth.domain.OAuthProvider
import com.github.nexters.ppotto.auth.presentation.dto.LoginRequest
import com.github.nexters.ppotto.auth.presentation.dto.LoginResponse
import com.github.nexters.ppotto.auth.presentation.dto.PendingTermResponse
import com.github.nexters.ppotto.auth.presentation.dto.TokenPairResponse
import com.github.nexters.ppotto.global.identifier.TermId
import com.github.nexters.ppotto.global.openapi.ApiExample
import com.github.nexters.ppotto.global.openapi.ApiExampleProvider
import com.github.nexters.ppotto.global.openapi.ApiExamples
import com.github.nexters.ppotto.global.openapi.OperationExamples
import com.github.nexters.ppotto.global.response.ApiResponse
import org.springframework.stereotype.Component
import java.util.UUID
import kotlin.reflect.KFunction

private const val ACCESS_TOKEN = "eyJhbGciOiJIUzI1NiJ9.sample-access-token.sample-signature"
private const val IDENTITY_TOKEN = "eyJhbGciOiJSUzI1NiJ9.sample-apple-identity-token.sample-signature"
private const val ACCESS_TOKEN_EXPIRES_IN = 3600L

private val KAKAO_LOGIN_REQUEST =
    ApiExample(
        name = "카카오 로그인",
        value =
            LoginRequest(
                provider = OAuthProvider.KAKAO,
                accessToken = "v1.sample-kakao-oauth-access-token",
                identityToken = null,
                authorizationCode = null,
                rawNonce = null,
                name = null,
            ),
    )

private val APPLE_FIRST_LOGIN_REQUEST =
    ApiExample(
        name = "애플 최초 로그인",
        value =
            LoginRequest(
                provider = OAuthProvider.APPLE,
                accessToken = null,
                identityToken = IDENTITY_TOKEN,
                authorizationCode = "sample.0.srtwx.apple-authorization-code",
                rawNonce = "4A7F0E2B-9C31-45D8-A6F2-8B0C3D9E1F52",
                name = "뽀또",
            ),
    )

private val APPLE_RELOGIN_REQUEST =
    ApiExample(
        name = "애플 재로그인",
        value =
            LoginRequest(
                provider = OAuthProvider.APPLE,
                accessToken = null,
                identityToken = IDENTITY_TOKEN,
                authorizationCode = "sample.0.mnpqr.apple-authorization-code",
                rawNonce = "0F9E8D7C-6B5A-4938-A716-05F4E3D2C1B0",
                name = null,
            ),
    )

private val NEW_USER_LOGIN_RESPONSE =
    ApiExample(
        name = "신규 가입 - 약관 동의 필요",
        value =
            ApiResponse.success(
                LoginResponse(
                    accessToken = ACCESS_TOKEN,
                    refreshToken = "sample-refresh-token-01983f2a7c317b02",
                    accessTokenExpiresIn = ACCESS_TOKEN_EXPIRES_IN,
                    isNewUser = true,
                    pendingTerms =
                        listOf(
                            PendingTermResponse(
                                id = TermId(UUID.fromString("01983f2a-1a2b-7c3d-8e4f-5a6b7c8d9e0f")),
                                code = "TOS",
                                version = "1.0",
                                isRequired = true,
                                contentUrl = "https://nexters.notion.site/ppotto-tos",
                                agreed = false,
                            ),
                            PendingTermResponse(
                                id = TermId(UUID.fromString("01983f2a-2b3c-7d4e-9f5a-6b7c8d9e0f1a")),
                                code = "PRIVACY",
                                version = "1.0",
                                isRequired = true,
                                contentUrl = "https://nexters.notion.site/ppotto-privacy",
                                agreed = false,
                            ),
                        ),
                ),
            ),
    )

private val RETURNING_USER_LOGIN_RESPONSE =
    ApiExample(
        name = "재로그인 - 바로 보드 진입",
        value =
            ApiResponse.success(
                LoginResponse(
                    accessToken = ACCESS_TOKEN,
                    refreshToken = "sample-refresh-token-01983f2a6b207a01",
                    accessTokenExpiresIn = ACCESS_TOKEN_EXPIRES_IN,
                    isNewUser = false,
                    pendingTerms = emptyList(),
                ),
            ),
    )

private val TOKEN_PAIR_RESPONSE =
    ApiExample(
        name = "재발급 성공",
        value =
            ApiResponse.success(
                TokenPairResponse(
                    accessToken = ACCESS_TOKEN,
                    refreshToken = "sample-refresh-token-01983f2a4d5e7f6a",
                    accessTokenExpiresIn = ACCESS_TOKEN_EXPIRES_IN,
                ),
            ),
    )

private val SOCIAL_AUTHENTICATION_FAILED =
    ApiExamples.errorExample(
        code = "AUTH-001",
        summary = "provider 토큰 검증 실패 (만료, 위조, aud/app_id 불일치, nonce 불일치)",
        message = "소셜 로그인 검증에 실패했습니다.",
    )

private val APPLE_CODE_EXCHANGE_FAILED =
    ApiExamples.errorExample(
        code = "AUTH-003",
        summary = "애플 authorization code 교환 실패 (만료 또는 재사용). 최초 로그인에서만 치명",
        message = "애플 인증 코드 교환에 실패했습니다. 다시 로그인해 주세요.",
    )

private val KAKAO_EMAIL_CONSENT_REQUIRED =
    ApiExamples.errorExample(
        code = "AUTH-004",
        summary = "카카오 이메일 동의 필요. account_email 추가 동의 후 재시도",
        message = "이메일 제공에 동의해야 가입할 수 있습니다.",
    )

private val KAKAO_NICKNAME_CONSENT_REQUIRED =
    ApiExamples.errorExample(
        code = "AUTH-005",
        summary = "카카오 닉네임 동의 필요. profile_nickname 추가 동의 후 재시도",
        message = "닉네임 제공에 동의해야 가입할 수 있습니다.",
    )

private val SIGNUP_NAME_REQUIRED =
    ApiExamples.errorExample(
        code = "AUTH-006",
        summary = "애플 신규 가입인데 name 미전달. 최초 인가에서 받은 이름을 함께 보내야 함",
        message = "가입에 필요한 이름이 전달되지 않았습니다.",
    )

private val SIGNUP_EMAIL_REQUIRED =
    ApiExamples.errorExample(
        code = "AUTH-007",
        summary = "애플 신규 가입인데 이메일 확보 실패. 애플 설정에서 앱 연동 해제 후 재로그인 필요",
        message = "가입에 필요한 이메일을 확인할 수 없습니다. 다시 로그인해 주세요.",
    )

private val INVALID_REFRESH_TOKEN =
    ApiExamples.errorExample(
        code = "AUTH-002",
        summary = "refresh token 만료 또는 위조. 재로그인 필요",
        message = "로그인이 만료되었습니다. 다시 로그인해 주세요.",
    )

@Component
class AuthApiExamples : ApiExampleProvider {
    override val examples: Map<KFunction<*>, OperationExamples> =
        mapOf(
            AuthApi::login to
                OperationExamples(
                    request = listOf(KAKAO_LOGIN_REQUEST, APPLE_FIRST_LOGIN_REQUEST, APPLE_RELOGIN_REQUEST),
                    responses =
                        mapOf(
                            "200" to listOf(NEW_USER_LOGIN_RESPONSE, RETURNING_USER_LOGIN_RESPONSE),
                            "400" to ApiExamples.INVALID_INPUT_RESPONSE + SIGNUP_NAME_REQUIRED + SIGNUP_EMAIL_REQUIRED,
                            "401" to listOf(SOCIAL_AUTHENTICATION_FAILED, APPLE_CODE_EXCHANGE_FAILED),
                            "403" to listOf(KAKAO_EMAIL_CONSENT_REQUIRED, KAKAO_NICKNAME_CONSENT_REQUIRED),
                        ),
                ),
            AuthApi::refresh to
                OperationExamples(
                    responses =
                        mapOf(
                            "200" to listOf(TOKEN_PAIR_RESPONSE),
                            "401" to listOf(INVALID_REFRESH_TOKEN),
                        ),
                ),
            AuthApi::logout to OperationExamples(responses = mapOf("200" to ApiExamples.EMPTY_SUCCESS)),
        )
}
