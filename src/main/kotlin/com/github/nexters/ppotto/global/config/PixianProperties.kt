package com.github.nexters.ppotto.global.config

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.net.URI

@Validated
@ConfigurationProperties(prefix = "pixian")
data class PixianProperties(
    @field:NotBlank
    val apiId: String,

    @field:NotBlank
    val apiSecret: String,

    @field:NotNull
    val testMode: Boolean,

    @field:NotNull
    val removeBackgroundUri: URI,
)
