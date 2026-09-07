package com.github.nexters.ppotto.notification.presentation

import com.github.nexters.ppotto.global.openapi.ApiExample
import com.github.nexters.ppotto.global.openapi.ApiExampleProvider
import com.github.nexters.ppotto.global.openapi.ApiExamples
import com.github.nexters.ppotto.global.openapi.OperationExamples
import com.github.nexters.ppotto.notification.domain.DevicePlatform
import com.github.nexters.ppotto.notification.presentation.dto.RegisterDeviceTokenRequest
import org.springframework.stereotype.Component
import kotlin.reflect.KFunction

private val REGISTER_DEVICE_TOKEN_REQUEST =
    ApiExample(
        name = "iOS 기기 등록",
        value =
            RegisterDeviceTokenRequest(
                deviceId = "3F2504E0-4F89-11D3-9A0C-0305E82C3301",
                platform = DevicePlatform.IOS,
                fcmToken = "dGhpcyBpcyBhIGRlbW8gZmNtIHRva2Vu",
            ),
    )

@Component
class DeviceTokenApiExamples : ApiExampleProvider {
    override val examples: Map<KFunction<*>, OperationExamples> =
        mapOf(
            DeviceTokenApi::register to
                OperationExamples(
                    request = listOf(REGISTER_DEVICE_TOKEN_REQUEST),
                    responses = mapOf("200" to ApiExamples.EMPTY_SUCCESS),
                ),
            DeviceTokenApi::unregister to
                OperationExamples(
                    responses = mapOf("200" to ApiExamples.EMPTY_SUCCESS),
                ),
        )
}
