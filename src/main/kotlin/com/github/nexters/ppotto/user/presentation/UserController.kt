package com.github.nexters.ppotto.user.presentation

import com.github.nexters.ppotto.global.response.ApiResponse
import com.github.nexters.ppotto.global.security.AuthenticatedUser
import com.github.nexters.ppotto.user.application.UserService
import com.github.nexters.ppotto.user.presentation.dto.UserResponse
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class UserController(
    private val userService: UserService,
) : UserApi {
    override fun getMe(
        @AuthenticatedUser userId: UUID,
    ): ApiResponse<UserResponse> {
        val user = userService.getById(userId)
        return ApiResponse.success(UserResponse.from(user))
    }

    override fun deleteMe(
        @AuthenticatedUser userId: UUID,
    ): ApiResponse<Unit> {
        userService.withdraw(userId)
        return ApiResponse.success()
    }
}
