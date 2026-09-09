package com.github.nexters.ppotto.global.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.task.SimpleAsyncTaskExecutor
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.scheduling.annotation.EnableAsync
import java.util.concurrent.Executor

@Configuration(proxyBeanMethods = false)
@EnableAsync
class AsyncConfig {
    @Bean(ANALYSIS_CLEANUP_TASK_EXECUTOR)
    @Profile("!test")
    fun analysisCleanupTaskExecutor(): Executor =
        SimpleAsyncTaskExecutor(
            Thread
                .ofVirtual()
                .name("analysis-cleanup-", 0)
                .factory(),
        )

    @Bean(ANALYSIS_CLEANUP_TASK_EXECUTOR)
    @Profile("test")
    fun testAnalysisCleanupTaskExecutor(): Executor = SyncTaskExecutor()

    @Bean(STICKER_IMAGE_CLEANUP_TASK_EXECUTOR)
    @Profile("!test")
    fun stickerImageCleanupTaskExecutor(): Executor =
        SimpleAsyncTaskExecutor(
            Thread
                .ofVirtual()
                .name("sticker-image-cleanup-", 0)
                .factory(),
        )

    @Bean(STICKER_IMAGE_CLEANUP_TASK_EXECUTOR)
    @Profile("test")
    fun testStickerImageCleanupTaskExecutor(): Executor = SyncTaskExecutor()

    @Bean(ANALYSIS_PIPELINE_TASK_EXECUTOR)
    @Profile("!test")
    fun analysisPipelineTaskExecutor(): Executor =
        SimpleAsyncTaskExecutor(
            Thread
                .ofVirtual()
                .name("analysis-pipeline-", 0)
                .factory(),
        )

    @Bean(ANALYSIS_PIPELINE_TASK_EXECUTOR)
    @Profile("test")
    fun testAnalysisPipelineTaskExecutor(): Executor = SyncTaskExecutor()

    @Bean(PUSH_NOTIFICATION_TASK_EXECUTOR)
    @Profile("!test")
    fun pushNotificationTaskExecutor(): Executor =
        SimpleAsyncTaskExecutor(
            Thread
                .ofVirtual()
                .name("push-notification-", 0)
                .factory(),
        )

    @Bean(PUSH_NOTIFICATION_TASK_EXECUTOR)
    @Profile("test")
    fun testPushNotificationTaskExecutor(): Executor = SyncTaskExecutor()

    companion object {
        const val ANALYSIS_CLEANUP_TASK_EXECUTOR = "analysisCleanupTaskExecutor"
        const val STICKER_IMAGE_CLEANUP_TASK_EXECUTOR = "stickerImageCleanupTaskExecutor"
        const val ANALYSIS_PIPELINE_TASK_EXECUTOR = "analysisPipelineTaskExecutor"
        const val PUSH_NOTIFICATION_TASK_EXECUTOR = "pushNotificationTaskExecutor"
    }
}
