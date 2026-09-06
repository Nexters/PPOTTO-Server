package com.github.nexters.ppotto.notification.infrastructure

import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.jooq.tables.references.USER_DEVICE_TOKENS
import com.github.nexters.ppotto.notification.domain.DevicePlatform
import org.jooq.DSLContext
import org.jooq.impl.DSL.excluded
import org.springframework.stereotype.Repository

@Repository
class DeviceTokenRepository(
    private val dslContext: DSLContext,
) {
    fun upsert(
        userId: UserId,
        deviceId: String,
        platform: DevicePlatform,
        fcmToken: String,
    ): Int =
        dslContext
            .insertInto(
                USER_DEVICE_TOKENS,
                USER_DEVICE_TOKENS.USER_ID,
                USER_DEVICE_TOKENS.DEVICE_ID,
                USER_DEVICE_TOKENS.PLATFORM,
                USER_DEVICE_TOKENS.FCM_TOKEN,
            ).values(userId, deviceId, platform.name, fcmToken)
            .onConflict(USER_DEVICE_TOKENS.USER_ID, USER_DEVICE_TOKENS.DEVICE_ID)
            .doUpdate()
            .set(USER_DEVICE_TOKENS.PLATFORM, excluded(USER_DEVICE_TOKENS.PLATFORM))
            .set(USER_DEVICE_TOKENS.FCM_TOKEN, excluded(USER_DEVICE_TOKENS.FCM_TOKEN))
            .execute()

    fun deleteByUserIdAndDeviceId(
        userId: UserId,
        deviceId: String,
    ): Int =
        dslContext
            .deleteFrom(USER_DEVICE_TOKENS)
            .where(USER_DEVICE_TOKENS.USER_ID.eq(userId))
            .and(USER_DEVICE_TOKENS.DEVICE_ID.eq(deviceId))
            .execute()

    fun findFcmTokensByUserId(userId: UserId): List<String> =
        dslContext
            .select(USER_DEVICE_TOKENS.FCM_TOKEN)
            .from(USER_DEVICE_TOKENS)
            .where(USER_DEVICE_TOKENS.USER_ID.eq(userId))
            .fetch(USER_DEVICE_TOKENS.FCM_TOKEN)
            .filterNotNull()

    fun deleteByFcmToken(fcmToken: String): Int =
        dslContext
            .deleteFrom(USER_DEVICE_TOKENS)
            .where(USER_DEVICE_TOKENS.FCM_TOKEN.eq(fcmToken))
            .execute()
}
