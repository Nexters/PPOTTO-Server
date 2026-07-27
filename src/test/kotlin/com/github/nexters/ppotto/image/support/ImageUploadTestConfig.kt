package com.github.nexters.ppotto.image.support

import com.github.nexters.ppotto.image.domain.ImageUploadUrlIssuer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class ImageUploadTestConfig {
    @Bean
    @Primary
    fun imageUploadUrlIssuer(): ImageUploadUrlIssuer = FakeImageUploadUrlIssuer()
}
