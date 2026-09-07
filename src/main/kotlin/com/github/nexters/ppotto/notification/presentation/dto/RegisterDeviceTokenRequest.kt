package com.github.nexters.ppotto.notification.presentation.dto

import com.github.nexters.ppotto.notification.domain.DevicePlatform
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

@Schema(description = "디바이스 토큰 등록/갱신 요청")
data class RegisterDeviceTokenRequest(
    @field:NotBlank
    @field:Schema(
        description = "클라이언트가 생성해 로컬에 영속시키는 안정적인 기기 식별자. FCM 토큰이 회전돼도 같은 값을 보내면 같은 기기로 upsert됨",
        example = "3F2504E0-4F89-11D3-9A0C-0305E82C3301",
    )
    val deviceId: String,

    @field:NotNull
    @field:Schema(description = "기기 플랫폼")
    val platform: DevicePlatform,

    @field:NotBlank
    @field:Schema(description = "FCM 토큰")
    val fcmToken: String,
)
