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

    @Bean(STICKER_IMAGE_CLEANUP_TASK_EXECUTOR)
    fun stickerImageCleanupTaskExecutor(): Executor =
        SimpleAsyncTaskExecutor(
            Thread
                .ofVirtual()
                .name("sticker-image-cleanup-", 0)
                .factory(),
        )

    @Bean(ANALYSIS_PIPELINE_TASK_EXECUTOR)
    fun analysisPipelineTaskExecutor(): Executor =
        SimpleAsyncTaskExecutor(
            Thread
                .ofVirtual()
                .name("analysis-pipeline-", 0)
                .factory(),
        )

    companion object {
        const val ANALYSIS_CLEANUP_TASK_EXECUTOR = "analysisCleanupTaskExecutor"
        const val STICKER_IMAGE_CLEANUP_TASK_EXECUTOR = "stickerImageCleanupTaskExecutor"
        const val ANALYSIS_PIPELINE_TASK_EXECUTOR = "analysisPipelineTaskExecutor"
    }
}
