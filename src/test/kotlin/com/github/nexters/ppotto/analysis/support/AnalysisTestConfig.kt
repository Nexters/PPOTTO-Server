package com.github.nexters.ppotto.analysis.support

import com.github.nexters.ppotto.analysis.domain.GeminiClassifier
import com.github.nexters.ppotto.analysis.domain.PhotoStorage
import com.github.nexters.ppotto.analysis.domain.StickerGenerator
import com.github.nexters.ppotto.analysis.domain.StickerStorage
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class AnalysisTestConfig {
    @Bean
    @Primary
    fun photoStorage(): PhotoStorage = FakePhotoStorage()

    @Bean
    @Primary
    fun geminiClassifier(): GeminiClassifier = FakeGeminiClassifier()

    @Bean
    @Primary
    fun stickerGenerator(): StickerGenerator = FakeStickerGenerator()

    @Bean
    @Primary
    fun stickerStorage(): StickerStorage = FakeStickerStorage()
}
