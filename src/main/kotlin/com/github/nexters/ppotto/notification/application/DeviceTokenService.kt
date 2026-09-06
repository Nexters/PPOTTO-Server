package com.github.nexters.ppotto.notification.application

import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.notification.domain.DevicePlatform
import com.github.nexters.ppotto.notification.infrastructure.DeviceTokenRepository
import org.springframework.stereotype.Service

@Service
class DeviceTokenService(
    private val deviceTokenRepository: DeviceTokenRepository,
) {
    fun register(
        userId: UserId,
        deviceId: String,
        platform: DevicePlatform,
        fcmToken: String,
    ) {
        deviceTokenRepository.upsert(userId, deviceId, platform, fcmToken)
    }

    fun unregister(
        userId: UserId,
        deviceId: String,
    ) {
        deviceTokenRepository.deleteByUserIdAndDeviceId(userId, deviceId)
    }
}
