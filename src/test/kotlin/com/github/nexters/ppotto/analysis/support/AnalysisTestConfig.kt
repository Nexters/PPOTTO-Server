package com.github.nexters.ppotto.analysis.support

import com.github.nexters.ppotto.analysis.domain.PhotoStorage
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class AnalysisTestConfig {
    @Bean
    @Primary
    fun photoStorage(): PhotoStorage = FakePhotoStorage()
}
