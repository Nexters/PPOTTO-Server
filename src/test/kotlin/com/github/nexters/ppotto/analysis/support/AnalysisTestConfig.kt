package com.github.nexters.ppotto.analysis.support

import com.github.nexters.ppotto.sticker.application.port.AnalysisPhotoOwnershipPort
import com.github.nexters.ppotto.sticker.application.port.AnalysisPhotoOwnershipScope
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class AnalysisTestConfig {
    @Bean
    @Primary
    fun photoStorage(): FakePhotoStorage = FakePhotoStorage()

    @Bean
    @Primary
    fun geminiClassifier(): FakeGeminiClassifier = FakeGeminiClassifier()

    @Bean
    @Primary
    fun stickerGenerator(): FakeStickerGenerator = FakeStickerGenerator()

    @Bean
    @Primary
    fun stickerStorage(): FakeStickerStorage = FakeStickerStorage()

    @Bean
    @Primary
    fun analysisPhotoOwnershipPort(): AnalysisPhotoOwnershipPort =
        object : AnalysisPhotoOwnershipPort {
            override fun matches(scope: AnalysisPhotoOwnershipScope): Boolean = true
        }
}
