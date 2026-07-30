package com.github.nexters.ppotto.user.presentation

import com.github.nexters.ppotto.global.response.ApiResponse
import com.github.nexters.ppotto.user.application.UserService
import com.github.nexters.ppotto.user.application.port.CurrentUserProvider
import com.github.nexters.ppotto.user.presentation.dto.UserResponse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/users", version = "1")
class UserController(
    private val currentUserProvider: CurrentUserProvider,
    private val userService: UserService,
) {
    @GetMapping("/me")
    fun getMe(): ApiResponse<UserResponse> {
        val user = userService.getById(currentUserProvider.currentUserId())
        return ApiResponse.success(UserResponse.from(user))
    }

    @DeleteMapping("/me")
    fun deleteMe(): ApiResponse<Unit> {
        userService.withdraw(currentUserProvider.currentUserId())
        return ApiResponse.success()
    }
}
