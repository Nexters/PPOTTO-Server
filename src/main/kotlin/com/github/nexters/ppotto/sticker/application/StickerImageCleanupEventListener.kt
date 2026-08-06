package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.global.config.AsyncConfig
import com.github.nexters.ppotto.sticker.application.port.StickerImageStoragePort
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class StickerImageCleanupEventListener(
    private val stickerImageStoragePorts: List<StickerImageStoragePort>,
) {
    @Async(AsyncConfig.STICKER_IMAGE_CLEANUP_TASK_EXECUTOR)
    @EventListener
    fun handle(event: StickerImageDeletionRequestedEvent) {
        if (event.imageKeys.isEmpty()) {
            return
        }

        runCatching {
            stickerImageStoragePort().deleteAll(event.imageKeys)
        }.onFailure {
            log.warn(
                "스티커 이미지 삭제 실패: stickerId={}, reason={}, imageKeys={}",
                event.stickerId,
                event.reason,
                event.imageKeys,
                it,
            )
        }
    }

    private fun stickerImageStoragePort(): StickerImageStoragePort =
        stickerImageStoragePorts.singleOrNull()
            ?: error("스티커 이미지 저장소 application port 구현이 정확히 하나 필요합니다.")

    companion object {
        private val log = LoggerFactory.getLogger(StickerImageCleanupEventListener::class.java)
    }
}
