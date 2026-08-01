package com.github.nexters.ppotto.user.presentation

import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.response.ApiResponse
import com.github.nexters.ppotto.global.security.AuthenticatedUser
import com.github.nexters.ppotto.user.application.UserService
import com.github.nexters.ppotto.user.presentation.dto.UserResponse
import org.springframework.web.bind.annotation.RestController

@RestController
class UserController(
    private val userService: UserService,
) : UserApi {
    override fun getMe(
        @AuthenticatedUser userId: UserId,
    ): ApiResponse<UserResponse> =
        userService
            .getById(userId)
            .let(UserResponse::from)
            .let { ApiResponse.success(it) }

    override fun deleteMe(
        @AuthenticatedUser userId: UserId,
    ): ApiResponse<Unit> =
        userService
            .withdraw(userId)
            .let { ApiResponse.success() }
}
