package com.github.nexters.ppotto.user.presentation

import com.github.nexters.ppotto.global.response.ApiResponse
import com.github.nexters.ppotto.user.presentation.dto.UserResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import java.util.UUID

@RequestMapping("/users", version = "1")
@Tag(name = "사용자", description = "내 계정 조회와 탈퇴")
interface UserApi {
    @GetMapping("/me")
    @Operation(
        summary = "내 정보 조회",
        description = "설정 화면에 필요한 현재 사용자 정보를 반환함",
    )
    fun getMe(userId: UUID): ApiResponse<UserResponse>

    @DeleteMapping("/me")
    @Operation(
        summary = "회원 탈퇴",
        description = "소셜 계정과 서비스 세션을 해지하고 사용자 데이터를 탈퇴 처리함",
    )
    fun deleteMe(userId: UUID): ApiResponse<Unit>
}
