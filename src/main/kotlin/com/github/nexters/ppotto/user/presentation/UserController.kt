package com.github.nexters.ppotto.user.presentation

import com.github.nexters.ppotto.global.error.UnauthorizedException
import com.github.nexters.ppotto.global.response.ApiResponse
import com.github.nexters.ppotto.user.application.UserService
import com.github.nexters.ppotto.user.presentation.dto.UserResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/users", version = "1")
class UserController(
    private val userService: UserService,
) {
    @GetMapping("/me")
    fun getMe(
        @AuthenticationPrincipal userId: UUID?,
    ): ApiResponse<UserResponse> {
        val user = userService.getById(userId ?: throw UnauthorizedException())
        return ApiResponse.success(UserResponse.from(user))
    }

    @DeleteMapping("/me")
    fun deleteMe(
        @AuthenticationPrincipal userId: UUID?,
    ): ApiResponse<Unit> {
        userService.withdraw(userId ?: throw UnauthorizedException())
        return ApiResponse.success()
    }
}
