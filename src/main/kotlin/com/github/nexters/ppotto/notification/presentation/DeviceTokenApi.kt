package com.github.nexters.ppotto.notification.presentation

import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.openapi.EmptySuccessApiResponse
import com.github.nexters.ppotto.global.response.ApiResponse
import com.github.nexters.ppotto.notification.presentation.dto.RegisterDeviceTokenRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import io.swagger.v3.oas.annotations.parameters.RequestBody as OpenApiRequestBody

@RequestMapping("/device-tokens", version = "1+")
@Tag(name = "디바이스 토큰", description = "푸시 알림 발송을 위한 FCM 디바이스 토큰 등록/해제")
interface DeviceTokenApi {
    @PostMapping
    @Operation(
        summary = "디바이스 토큰 등록/갱신",
        description = "deviceId 기준으로 upsert함. FCM 토큰이 회전돼도 같은 deviceId를 보내면 같은 기기 행이 갱신됨",
        requestBody =
            OpenApiRequestBody(
                required = true,
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = RegisterDeviceTokenRequest::class),
                    ),
                ],
            ),
    )
    @EmptySuccessApiResponse
    fun register(
        userId: UserId,
        request: RegisterDeviceTokenRequest,
    ): ApiResponse<Unit>

    @DeleteMapping
    @Operation(
        summary = "디바이스 토큰 해제",
        description = "로그아웃 등으로 더 이상 알림을 받지 않을 기기의 토큰을 삭제함",
        parameters = [
            Parameter(name = "deviceId", description = "해제할 기기 식별자", example = "3F2504E0-4F89-11D3-9A0C-0305E82C3301"),
        ],
    )
    @EmptySuccessApiResponse
    fun unregister(
        userId: UserId,
        deviceId: String,
    ): ApiResponse<Unit>
}
