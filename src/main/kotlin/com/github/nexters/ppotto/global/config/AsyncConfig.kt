package com.github.nexters.ppotto.global.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.SimpleAsyncTaskExecutor
import org.springframework.scheduling.annotation.EnableAsync
import java.util.concurrent.Executor

@Configuration
@EnableAsync
class AsyncConfig {
    @Bean(ANALYSIS_CLEANUP_TASK_EXECUTOR)
    fun analysisCleanupTaskExecutor(): Executor =
        SimpleAsyncTaskExecutor(
            Thread
                .ofVirtual()
                .name("analysis-cleanup-", 0)
                .factory(),
        )

    companion object {
        const val ANALYSIS_CLEANUP_TASK_EXECUTOR = "analysisCleanupTaskExecutor"
    }
}
