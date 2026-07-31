package com.github.nexters.ppotto.analysis.support

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
}
