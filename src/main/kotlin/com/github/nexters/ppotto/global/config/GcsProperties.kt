package com.github.nexters.ppotto.global.config

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "gcs")
data class GcsProperties(
    @field:NotBlank
    val bucket: String,
    @field:NotBlank
    val credentialsPath: String,
    @field:Positive
    @field:Max(SIGNED_URL_MAX_EXPIRATION_MINUTES)
    val signedUrlExpirationMinutes: Long,
)

// GCS V4 signed URL의 실제 프로토콜 상한은 7일이지만, URL 유출 시 악용 가능 기간을 줄이기 위해 서비스
// 정책으로 1일을 상한으로 둔다. SDK는 이 상한을 검증하지 않고 실제 서명된 URL 사용 시점에만 서버가
// 거부하므로, 잘못된 설정을 기동 시점에 잡아내기 위해 애플리케이션 레벨에서 직접 검증한다.
private const val SIGNED_URL_MAX_EXPIRATION_MINUTES = 24L * 60
