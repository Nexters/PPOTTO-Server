package com.github.nexters.ppotto.notification.presentation

import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.security.AuthenticatedUser
import com.github.nexters.ppotto.global.web.ApiResponse
import com.github.nexters.ppotto.notification.application.DeviceTokenService
import com.github.nexters.ppotto.notification.presentation.dto.RegisterDeviceTokenRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class DeviceTokenController(
    private val deviceTokenService: DeviceTokenService,
) : DeviceTokenApi {
    override fun register(
        @AuthenticatedUser userId: UserId,
        @Valid @RequestBody request: RegisterDeviceTokenRequest,
    ): ApiResponse<Unit> =
        deviceTokenService
            .register(userId, request.deviceId, request.platform, request.fcmToken)
            .let { ApiResponse.success() }

    override fun unregister(
        @AuthenticatedUser userId: UserId,
        @RequestParam deviceId: String,
    ): ApiResponse<Unit> =
        deviceTokenService
            .unregister(userId, deviceId)
            .let { ApiResponse.success() }
}
