package com.github.nexters.ppotto.user.presentation

import com.github.nexters.ppotto.global.openapi.EmptySuccessApiResponse
import com.github.nexters.ppotto.global.response.ApiResponse
import com.github.nexters.ppotto.user.presentation.dto.UserApiExamples
import com.github.nexters.ppotto.user.presentation.dto.UserResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import java.util.UUID
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

@RequestMapping("/users", version = "1")
@Tag(name = "사용자", description = "내 계정 조회와 탈퇴")
interface UserApi {
    @GetMapping("/me")
    @Operation(
        summary = "내 정보 조회",
        description = "설정 화면에 필요한 현재 사용자 정보를 반환함",
    )
    @OpenApiResponse(
        responseCode = "200",
        useReturnTypeSchema = true,
        description = "내 정보",
        content = [
            Content(
                examples = [
                    ExampleObject(
                        name = "카카오 사용자",
                        value = UserApiExamples.KAKAO_USER_RESPONSE,
                    ),
                    ExampleObject(
                        name = "애플 사용자 - 이메일 가리기 (private relay)",
                        value = UserApiExamples.APPLE_PRIVATE_RELAY_USER_RESPONSE,
                    ),
                ],
            ),
        ],
    )
    fun getMe(userId: UUID): ApiResponse<UserResponse>

    @DeleteMapping("/me")
    @Operation(
        summary = "회원 탈퇴",
        description = "소셜 계정과 서비스 세션을 해지하고 사용자 데이터를 탈퇴 처리함",
    )
    @EmptySuccessApiResponse
    fun deleteMe(userId: UUID): ApiResponse<Unit>
}
