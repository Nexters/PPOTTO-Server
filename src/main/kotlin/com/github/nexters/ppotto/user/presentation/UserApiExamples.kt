package com.github.nexters.ppotto.user.presentation

import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.openapi.ApiExample
import com.github.nexters.ppotto.global.openapi.ApiExampleProvider
import com.github.nexters.ppotto.global.openapi.ApiExamples
import com.github.nexters.ppotto.global.openapi.OperationExamples
import com.github.nexters.ppotto.global.response.ApiResponse
import com.github.nexters.ppotto.user.domain.OAuthProvider
import com.github.nexters.ppotto.user.presentation.dto.UserResponse
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import kotlin.reflect.KFunction

private val KAKAO_USER_RESPONSE =
    ApiExample(
        name = "카카오 사용자",
        value =
            ApiResponse.success(
                UserResponse(
                    id = UserId(UUID.fromString("01983f2a-7c31-7b02-93d4-1f2e3d4c5b6a")),
                    provider = OAuthProvider.KAKAO,
                    email = "ppotto@kakao.com",
                    name = "뽀또",
                    createdAt = Instant.parse("2026-07-01T00:12:33Z"),
                ),
            ),
    )

private val APPLE_PRIVATE_RELAY_USER_RESPONSE =
    ApiExample(
        name = "애플 사용자 - 이메일 가리기 (private relay)",
        value =
            ApiResponse.success(
                UserResponse(
                    id = UserId(UUID.fromString("01983f2a-6b20-7a01-82c3-0e1d2c3b4a59")),
                    provider = OAuthProvider.APPLE,
                    email = "mxq7r2v9td@privaterelay.appleid.com",
                    name = "홍길동",
                    createdAt = Instant.parse("2026-07-15T12:40:05Z"),
                ),
            ),
    )

@Component
class UserApiExamples : ApiExampleProvider {
    override val examples: Map<KFunction<*>, OperationExamples> =
        mapOf(
            UserApi::getMe to
                OperationExamples(
                    responses = mapOf("200" to listOf(KAKAO_USER_RESPONSE, APPLE_PRIVATE_RELAY_USER_RESPONSE)),
                ),
            UserApi::deleteMe to OperationExamples(responses = mapOf("200" to ApiExamples.EMPTY_SUCCESS)),
        )
}
