package com.github.nexters.ppotto.analysis.support

import com.github.nexters.ppotto.global.storage.GcsReadUrlIssuer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class AnalysisTestConfig {
    @Bean
    @Primary
    fun photoStorage(gcsReadUrlIssuer: GcsReadUrlIssuer): FakePhotoStorage = FakePhotoStorage(gcsReadUrlIssuer)

    @Bean
    @Primary
    fun themeClassifier(): FakeThemeClassifier = FakeThemeClassifier()

    @Bean
    @Primary
    fun stickerGenerator(): FakeStickerGenerator = FakeStickerGenerator()

    @Bean
    @Primary
    fun stickerStorage(): FakeStickerStorage = FakeStickerStorage()
}
