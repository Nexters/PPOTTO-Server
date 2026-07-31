package com.github.nexters.ppotto.sticker.support

import com.github.nexters.ppotto.sticker.application.port.RecapPhotoQueryPort
import com.github.nexters.ppotto.sticker.application.port.StickerImageQueryPort
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class StickerTestConfig {
    @Bean
    @Primary
    fun fakeAnalysisPhotoOwnershipPort(): FakeAnalysisPhotoOwnershipPort = FakeAnalysisPhotoOwnershipPort()

    @Bean
    @Primary
    fun fakeRecapPhotoQueryPort(): RecapPhotoQueryPort = FakeRecapPhotoQueryPort()

    @Bean
    @Primary
    fun fakeStickerImageQueryPort(): StickerImageQueryPort = FakeStickerImageQueryPort()
}
